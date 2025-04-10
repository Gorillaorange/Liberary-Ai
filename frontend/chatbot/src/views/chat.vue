<script lang="tsx" setup>
import { defaultMockModelName, modelMappingList, triggerModelTermination } from '@/components/MarkdownPreview/models'
import { type InputInst } from 'naive-ui'
import type { SelectBaseOption } from 'naive-ui/es/select/src/interface'
import { isGithubDeployed } from '@/config'
import { logout } from '@/api/auth'
import { useSessionStore } from '@/store/hooks/useSessionStore'
import { useUserProfileStore } from '@/store/hooks/useUserProfileStore'

import { UAParser } from 'ua-parser-js'
import SideBar from '@/components/SideBar/index.vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import MarkdownPreview from '@/components/MarkdownPreview/index.vue'
import ParticlesBackground from '@/components/ParticlesBackground.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const businessStore = useBusinessStore()
const sessionStore = useSessionStore()
const userProfileStore = useUserProfileStore()

// 消息类型接口定义
interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: number;
}

// 对话历史记录
const chatHistory = ref<ChatMessage[]>([]);

// 消息容器引用
const chatMessagesRef = ref<HTMLElement | null>(null);

// 滚动到最新消息
const scrollToBottom = () => {
  nextTick(() => {
    if (chatMessagesRef.value) {
      chatMessagesRef.value.scrollTop = chatMessagesRef.value.scrollHeight;
    }
  });
};

// 在组件挂载时初始化
onMounted(async () => {
  // 初始化会话
  await sessionStore.initialize()
  // 加载用户画像
  userProfileStore.loadUserProfile()
})

// 监听当前会话消息变化
watch(() => sessionStore.currentMessages, (messages) => {
  console.log("会话消息更新:", messages);
  if (messages.length > 0) {
    // 将会话消息转换为聊天历史格式
    chatHistory.value = messages.map(msg => ({
      id: msg.id,
      role: msg.role as 'user' | 'assistant',
      content: msg.content,
      timestamp: new Date(msg.createTime).getTime()
    }));
  } else {
    // 如果没有消息，显示默认欢迎消息
    chatHistory.value = [{
      id: Date.now().toString(),
      role: 'assistant',
      content: '新的对话开始了，有什么我可以帮到你的吗？',
      timestamp: Date.now()
    }];
  }
  
  // 滚动到底部
  scrollToBottom();
}, { immediate: true, deep: true });

// 添加用户消息到历史记录并同步到会话
const addUserMessage = async (content: string) => {
  // 如果没有当前会话，创建一个新会话
  if (!sessionStore.currentSessionId) {
    const sessionId = await sessionStore.createNewSession();
    if (!sessionId) {
      message.error('创建会话失败');
      return;
    }
  }
  
  // 临时添加到本地显示
  chatHistory.value.push({
    id: Date.now().toString(),
    role: 'user',
    content,
    timestamp: Date.now()
  });
  
  // 发送到后端
  await sessionStore.addMessage(sessionStore.currentSessionId, 'user', content);
  
  // 分析用户消息内容更新用户画像
  userProfileStore.analyzeChat(content);
  
  // 滚动到底部
  scrollToBottom();
}

// 添加AI回复到历史记录并同步到会话
const addAssistantMessage = async (content: string) => {
  if (!sessionStore.currentSessionId) return;
  
  // 添加到后端
  await sessionStore.addMessage(sessionStore.currentSessionId, 'assistant', content);
  
  // 分析AI回复内容更新用户画像
  userProfileStore.analyzeChat(content);
}


const modelListSelections = computed(() => {
  return modelMappingList.map<SelectBaseOption>((modelItem) => {
    let disabled = false
    if (isGithubDeployed && modelItem.modelName !== defaultMockModelName) {
      disabled = true
    }

    return {
      label: modelItem.label,
      value: modelItem.modelName,
      // Github 演示环境禁用模型切换，拉取代码后可按自己需求修改
      disabled
    }
  })
})


const loading = ref(true)

setTimeout(() => {
  loading.value = false
}, 700)


const stylizingLoading = ref(false)


/**
 * 输入字符串
 */
const inputTextString = ref('')
const refInputTextString = ref<InputInst | null>()

/**
 * 输出字符串 Reader 流（风格化的）
 */
const outputTextReader = ref<ReadableStreamDefaultReader | null>()

const refReaderMarkdownPreview = ref<any>()

const onFailedReader = () => {
  outputTextReader.value = null
  stylizingLoading.value = false
  if (refReaderMarkdownPreview.value) {
    refReaderMarkdownPreview.value.initializeEnd()
  }
  window.$ModalMessage.error('转换失败，请重试')
  setTimeout(() => {
    if (refInputTextString.value) {
      refInputTextString.value.focus()
    }
  })
  triggerModelTermination()
}
const onCompletedReader = () => {
  stylizingLoading.value = false
  setTimeout(() => {
    if (refInputTextString.value) {
      refInputTextString.value.focus()
    }
  })
  triggerModelTermination()
}

// 渲染Markdown文本
const renderMarkdown = (text: string): string => {
  if (!text) return '';
  try {
    // 先预处理文本，清理特殊标签
    let processedText = text
      // 思考过程处理 - 添加样式并确保正确显示
      .replace(/<think>([\s\S]*?)<\/think>/g, '<div class="thinking-process"><h4>🤔 思考过程</h4>$1</div>')
      // 将<br>标签转换为换行符，以便Markdown正确处理
      .replace(/<br\s*\/?>/g, '\n')
      // 处理可能存在的HTML实体字符
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&quot;/g, '"')
      .replace(/&amp;/g, '&');
      
    // 移除可能出现在文本末尾的用户身份信息
    processedText = processedText.replace(/该同学为[^，]*学生，[^，]*专业，回答时需考虑用户身份。$/g, '');
    
    // 使用marked处理Markdown
    const rawHtml = marked.parse(processedText) as string;
    
    // 清理HTML以防XSS攻击
    const cleanHtml = DOMPurify.sanitize(rawHtml, {
      ADD_TAGS: ['style', 'div'],
      ADD_ATTR: ['target', 'rel', 'href', 'class']
    });
    
    return cleanHtml;
  } catch (error: any) {
    console.error('Markdown渲染错误', error);
    return `<p>渲染错误: ${error?.message || '未知错误'}</p>`;
  }
};

const handleCreateStylized = async () => {
  // 若正在加载，则点击后恢复初始状态
  if (stylizingLoading.value) {
    refReaderMarkdownPreview.value.abortReader()
    onCompletedReader()
    return
  }

  // 验证输入内容
  if (!inputTextString.value.trim()) {
    inputTextString.value = ''
    refInputTextString.value?.focus()
    return
  }

  // 获取用户消息内容
  const userContent = inputTextString.value.trim();
  const userMsgId = `user-${Date.now()}`;
  const assistantMsgId = `assistant-${Date.now()}`;
  
  try {
    console.log("准备发送用户消息:", userContent);
    
    // 1. 先添加用户消息到UI
    chatHistory.value.push({
      id: userMsgId,
      role: 'user',
      content: userContent,
      timestamp: Date.now()
    });
    
    // 2. 清空输入框
    inputTextString.value = '';
    
    // 3. 设置加载状态
    stylizingLoading.value = true;
    
    // 4. 添加空的AI回复消息占位
    chatHistory.value.push({
      id: assistantMsgId,
      role: 'assistant',
      content: '',
      timestamp: Date.now()
    });
    
    // 5. 滚动到底部确保用户可以看到AI输入区域
    scrollToBottom();
    
    // 6. 重置并准备Markdown预览组件
    if (refReaderMarkdownPreview.value) {
      console.log("重置Markdown预览组件");
      refReaderMarkdownPreview.value.resetStatus();
      refReaderMarkdownPreview.value.initializeStart();
      
      // 设置文本更新回调
      refReaderMarkdownPreview.value.onTextUpdate((text) => {
        if (!text) return;
        
        console.log("收到文本更新，长度:", text.length);
        
        // 预处理文本内容
        let processedText = text
          .replace(/<!--[\s\S]*?-->/g, '') // 移除注释
          .replace(/<br\s*\/?>/g, '\n');   // 将<br>转换为换行符
        
        // 查找并更新AI回复消息
        const msgIndex = chatHistory.value.findIndex(msg => msg.id === assistantMsgId);
        if (msgIndex !== -1) {
          console.log("更新AI回复消息内容");
          // 确保消息角色正确
          chatHistory.value[msgIndex].role = 'assistant';
          chatHistory.value[msgIndex].content = processedText;
        } else {
          console.error("错误：找不到AI回复消息，创建新消息");
          // 如果找不到占位消息，创建新消息
          chatHistory.value.push({
            id: `assistant-fallback-${Date.now()}`,
            role: 'assistant',
            content: processedText,
            timestamp: Date.now()
          });
        }
        
        // 更新后滚动到底部
        scrollToBottom();
      });
      
      // 设置完成回调
      refReaderMarkdownPreview.value.onComplete(() => {
        console.log("AI回复完成");
        stylizingLoading.value = false;
        scrollToBottom();
      });
    }
    
    // 7. 设置超时处理
    const timeout = setTimeout(() => {
      if (stylizingLoading.value) {
        console.log("请求超时");
        if (refReaderMarkdownPreview.value) {
          refReaderMarkdownPreview.value.abortReader();
        }
        stylizingLoading.value = false;
        
        // 更新为超时消息
        const msgIndex = chatHistory.value.findIndex(msg => msg.id === assistantMsgId);
        if (msgIndex !== -1) {
          chatHistory.value[msgIndex].content = `
<div style="padding: 10px; border-left: 4px solid #faad14; background-color: #fffbe6; margin-bottom: 10px;">
  <div style="font-weight: bold; color: #d48806; margin-bottom: 5px;">请求超时</div>
  <div>服务器响应时间过长，请稍后再试。</div>
</div>`;
        }
      }
    }, 60000); // 60秒超时
    
    // 8. 调用API获取回复
    console.log("发送请求 - 会话ID:", sessionStore.currentSessionId);
    
    const { error, reader } = await businessStore.createAssistantWriterStylized({
      text: userContent,
      sessionId: sessionStore.currentSessionId,
      withHistory: true
    });
    
    // 9. 清除超时计时器
    clearTimeout(timeout);
    
    // 10. 处理错误和设置reader
    if (error) {
      console.error("API返回错误:", error);
      throw new Error(String(error));
    }
    
    if (reader) {
      console.log("获取到响应流，设置reader");
      outputTextReader.value = reader;
    } else {
      console.error("未获取到响应流");
      stylizingLoading.value = false;
      throw new Error("未获取到响应流");
    }
  } catch (error) {
    console.error("聊天请求出错:", error);
    stylizingLoading.value = false;
    
    // 显示错误消息
    const errorMessage = error instanceof Error ? error.message : "发生未知错误";
    const msgIndex = chatHistory.value.findIndex(msg => msg.id === assistantMsgId);
    
    if (msgIndex !== -1) {
      chatHistory.value[msgIndex].content = `
<div style="padding: 10px; border-left: 4px solid #ff4d4f; background-color: #fff2f0; margin-bottom: 10px;">
  <div style="font-weight: bold; color: #cf1322; margin-bottom: 5px;">请求错误</div>
  <div>${errorMessage}</div>
</div>`;
    }
  }
}


const keys = useMagicKeys()
const enterCommand = keys['Meta+Enter']
const enterCtrl = keys['Ctrl+Enter']

const activeElement = useActiveElement()
const notUsingInput = computed(() => activeElement.value?.tagName !== 'TEXTAREA')

const parser = new UAParser()
const isMacos = computed(() => {
  const os = parser.getOS()
  if (!os) return

  return os.name?.includes?.('macos')
})

const placeholder = computed(() => {
  if (stylizingLoading.value) {
    return `AI正在回复中...`
  }
  return `输入消息，按 ${ isMacos ? 'Command' : 'Ctrl' } + Enter 键发送...`
})

watch(
  () => enterCommand.value,
  () => {
    if (!isMacos || notUsingInput.value) return

    if (stylizingLoading.value) return

    if (!enterCommand.value) {
      handleCreateStylized()
    }
  },
  {
    deep: true
  }
)

watch(
  () => enterCtrl.value,
  () => {
    if (isMacos || notUsingInput.value) return

    if (stylizingLoading.value) return

    if (!enterCtrl.value) {
      handleCreateStylized()
    }
  },
  {
    deep: true
  }
)


const handleResetState = () => {
  inputTextString.value = ''

  stylizingLoading.value = false
  nextTick(() => {
    refInputTextString.value?.focus()
  })
  refReaderMarkdownPreview.value?.abortReader()
  refReaderMarkdownPreview.value?.resetStatus()
}
handleResetState()


const PromptTag = defineComponent({
  props: {
    text: {
      type: String,
      default: ''
    }
  },
  setup(props) {
    const handleClick = () => {
      inputTextString.value = props.text
      nextTick(() => {
        refInputTextString.value?.focus()
      })
    }
    return {
      handleClick
    }
  },
  render() {
    return (
      <div
        b="~ solid transparent"
        hover="shadow-[--shadow]"
        class={[
          'px-10 py-2 rounded-7 text-12',
          'max-w-230 transition-all-300 select-none cursor-pointer',
          'prompt-tag'
        ]}
        style={{
          '--shadow': '3px 3px 3px -1px rgba(0,0,0,0.2)'
        }}
        onClick={this.handleClick}
      >
        <n-ellipsis
          tooltip={{
            contentClass: 'wrapper-tooltip-scroller',
            keepAliveOnHover: true
          }}
        >
          {{
            tooltip: () => this.text,
            default: () => this.text
          }}
        </n-ellipsis>
      </div>
    )
  }
})

const promptTextList = ref([
  '打个招呼吧，并告诉我你的名字',
  '可以推荐一些网络工程入门书籍吗？'
])

// 创建新的会话
const createNewChat = async () => {
  await sessionStore.createNewSession();
}

// 清空当前对话，使用会话管理
const clearCurrentChat = async () => {
  if (sessionStore.currentSessionId) {
    if (confirm('确定要清空当前会话吗？')) {
      await sessionStore.removeSession(sessionStore.currentSessionId);
    }
  } else {
    chatHistory.value = [{
      id: Date.now().toString(),
      role: 'assistant',
      content: '你好！我是图书问答员，有什么我可以帮到你的吗？',
      timestamp: Date.now()
    }];
  }
}

// 格式化时间
const formatTime = (timestamp: number) => {
  const date = new Date(timestamp);
  return `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`;
}
</script>

<template>
  <div class="chat-container">
    <ParticlesBackground />
    
    <SideBar />
    <div class="chat-content">
      <LayoutCenterPanel
        title="智能助手"
        :loading="loading"
        is-center
      >
        <div
          flex="~ col"
          h-full
        >
          <div
            flex="~ justify-between items-center"
          >
            <NavigationNavBar>
              <template #bottom>
                <div
                  flex="~ justify-center items-center wrap"
                  class="pt-10 text-16"
                >
                  <span>当前模型：</span>
                  <div
                    flex="~ justify-center items-center"
                  >
                    <n-select
                      v-model:value="businessStore.systemModelName"
                      class="w-280 pr-10 font-italic font-bold"
                      placeholder="请选择模型"
                      :disabled="stylizingLoading"
                      :options="modelListSelections"
                    />
                    <CustomTooltip
                      :disabled="false"
                    >
                      <div>注意：</div>
                      <div>
                        数据模拟阶段测试中...
                      </div>
                      
                      <template #trigger>
                        <span class="cursor-help font-bold c-primary text-17 i-radix-icons:question-mark-circled"></span>
                      </template>
                    </CustomTooltip>
                    <n-button class="ml-4" @click="clearCurrentChat" :disabled="stylizingLoading">
                      <template #icon>
                        <div class="i-carbon:delete"></div>
                      </template>
                      清空对话
                    </n-button>
                  </div>
                </div>
              </template>
            </NavigationNavBar>
          </div>

          <!-- 聊天消息区域 -->
          <div 
            class="chat-messages"
            ref="chatMessagesRef"
            flex="1 ~ col"
            min-h-0
            pb-20
            overflow-y-auto
          >
            <div class="message-container">
              <!-- 消息列表 -->
              <template v-for="msg in chatHistory" :key="msg.id">
                <!-- 用户消息 - 右侧显示 -->
                <div 
                  v-if="msg.role === 'user'"
                  class="message-wrapper user-message"
                >
                  <div class="message-avatar">
                    <div class="i-carbon:user-avatar"></div>
                  </div>
                  <div class="message-content">
                    <div class="message-header">
                      <div class="message-name">我</div>
                      <div class="message-time">{{ formatTime(msg.timestamp) }}</div>
                    </div>
                    <div class="message-body">
                      {{ msg.content }}
                    </div>
                  </div>
                </div>
                
                <!-- AI消息 - 左侧显示 -->
                <div 
                  v-else
                  class="message-wrapper assistant-message"
                >
                  <div class="message-avatar">
                    <div class="i-carbon:bot"></div>
                  </div>
                  <div class="message-content">
                    <div class="message-header">
                      <div class="message-name">AI助手</div>
                      <div class="message-time">{{ formatTime(msg.timestamp) }}</div>
                    </div>
                    <div 
                      class="message-body markdown-body"
                      v-html="renderMarkdown(msg.content)"
                    >
                    </div>
                  </div>
                </div>
              </template>
              
              <!-- AI思考状态 - 仅当正在加载且没有空消息时显示 -->
              <div 
                v-if="stylizingLoading && !chatHistory.some(msg => msg.role === 'assistant' && msg.content === '')" 
                class="message-wrapper assistant-message thinking-indicator"
              >
                <div class="message-avatar">
                  <div class="i-carbon:bot"></div>
                </div>
                <div class="message-content">
                  <div class="message-header">
                    <div class="message-name">AI助手</div>
                  </div>
                  <div class="message-body typing">
                    <div class="typing-dot"></div>
                    <div class="typing-dot"></div>
                    <div class="typing-dot"></div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 快捷提问区域 -->
          <div
            flex="~ justify-start"
            p="10px 20px"
            bg="#f9f9f9"
            border-top="1px solid #eee"
          >
            <n-space>
              <PromptTag
                v-for="(textItem, idx) in promptTextList"
                :key="idx"
                :text="textItem"
              />
            </n-space>
          </div>

          <!-- 输入区域 -->
          <div
            flex="~ col items-center"
            p="14px"
            bg="#fff"
            border-top="1px solid #eee"
          >
            <div
              relative
              flex="1"
              w-full
              px-1em
            >
              <n-input
                ref="refInputTextString"
                v-model:value="inputTextString"
                type="textarea"
                autofocus
                h-full
                class="textarea-resize-none text-15"
                :style="{
                  '--n-border-radius': '20px',
                  '--n-padding-left': '20px',
                  '--n-padding-right': '60px',
                  '--n-padding-vertical': '10px',
                  'min-height': '50px',
                  'max-height': '120px'
                }"
                :placeholder="placeholder"
                :disabled="stylizingLoading"
              />
              <n-float-button
                position="absolute"
                :right="20"
                bottom="50%"
                :type="stylizingLoading ? 'primary' : 'default'"
                color
                :class="[
                  stylizingLoading && 'opacity-90',
                  'translate-y-50%'
                ]"
                @click.stop="handleCreateStylized()"
              >
                <div
                  v-if="stylizingLoading"
                  class="i-svg-spinners:pulse-2 c-#fff"
                ></div>
                <div
                  v-else
                  class="i-carbon:send-filled text-18"
                ></div>
              </n-float-button>
            </div>
          </div>
        </div>
      </LayoutCenterPanel>
    </div>
    
    <!-- 隐藏的MarkdownPreview组件，用于处理流式响应 -->
    <div class="hidden-markdown-preview">
      <MarkdownPreview
        ref="refReaderMarkdownPreview"
        v-model:reader="outputTextReader"
        :model="businessStore.currentModelItem?.modelName"
        :transform-stream-fn="businessStore.currentModelItem?.transformStreamValue"
        text=""
        :inversion="false"
        @failed="onFailedReader"
        @completed="onCompletedReader"
      />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.chat-container {
  display: flex;
  height: 100vh;
  overflow: hidden;
  position: relative;
}

.chat-content {
  flex: 1;
  min-width: 0; /* 重要：防止弹性项目溢出父容器 */
  
  :deep(.panel-shadow) {
    display: flex;
    width: 100%;
    background-color: transparent;
    box-shadow: none;
  }
  
  :deep(section) {
    width: 100%;
  }
  
  :deep(.slot-frame-layout-container) {
    width: 100%;
    background-color: transparent;
  }
  
  :deep(.center) {
    flex: 1;
    width: 100%;
  }
  
  :deep(.n-spin-container) {
    width: 100%;
  }
  
  // 使导航区域透明化
  :deep(.navigation-container) {
    background-color: rgba(26, 35, 51, 0.7);
    backdrop-filter: blur(5px);
    border-bottom: 1px solid var(--card-border-dark);
    
    .n-select {
      .n-base-selection {
        background-color: var(--secondary-dark);
        border-color: var(--card-border-dark);
        
        .n-base-selection-input {
          color: var(--text-light);
        }
        
        .n-base-selection-placeholder {
          color: var(--text-light-secondary);
        }
      }
    }
    
    .n-button {
      background-color: var(--accent-color);
      border-color: transparent;
      color: white;
      
      &:hover {
        background-color: var(--accent-hover);
      }
    }
  }
}

.chat-messages {
  padding: 20px;
  background-color: rgba(26, 35, 51, 0.1);
  backdrop-filter: blur(0.1px);
  height: 100%;
  overflow-y: auto;
  scroll-behavior: smooth;
}

.message-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-wrapper {
  display: flex;
  align-items: flex-start;
  max-width: 88%;
  margin-bottom: 16px;
  
  // 思考状态指示器特殊样式
  &.thinking-indicator {
    opacity: 0.8;
    max-width: 220px;
    
    .message-content {
      background-color: rgba(38, 50, 71, 0.3);
      
      .message-body {
        min-height: 24px;
        display: flex;
        align-items: center;
        justify-content: center;
      }
    }
  }

  &.user-message {
    align-self: flex-end;
    flex-direction: row-reverse;
    margin-left: auto; /* 确保用户消息靠右 */
    
    .message-avatar {
      margin-left: 12px;
      margin-right: 0;
      background-color: var(--accent-color);
    }
    
    .message-content {
      background-color: rgba(74, 111, 164, 0.2);
      border: 1px solid rgba(74, 111, 164, 0.3);
      border-radius: 12px 0 12px 12px;
    }
    
    .message-header {
      flex-direction: row-reverse;
    }
  }
  
  &.assistant-message {
    align-self: flex-start;
    flex-direction: row;
    margin-right: auto; /* 确保AI消息靠左 */
    
    .message-avatar {
      margin-right: 12px;
      margin-left: 0;
      background-color: var(--secondary-dark);
    }
    
    .message-content {
      background-color: rgba(38, 50, 71, 0.4);
      border: 1px solid var(--card-border-dark);
      border-radius: 0 12px 12px 12px;
    }
  }
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
  flex-shrink: 0;
  
  div {
    font-size: 24px;
    color: var(--text-light);
  }
}

.message-content {
  padding: 12px;
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.15);
  min-width: 60px;
  max-width: 100%;
  backdrop-filter: blur(8px);
}

.message-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 12px;
}

.message-name {
  font-weight: 600;
  color: var(--text-light);
}

.message-time {
  color: var(--text-light-secondary);
  font-size: 11px;
}

.message-body {
  font-size: 14px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-light);
  
  :deep(br) {
    content: "";
    display: block;
    margin-top: 5px;
  }
}

.typing {
  display: flex;
  align-items: center;
  height: 24px;
}

.typing-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: var(--text-light);
  margin: 0 2px;
  animation: typing 1s infinite ease-in-out;
  
  &:nth-child(2) {
    animation-delay: 0.2s;
  }
  
  &:nth-child(3) {
    animation-delay: 0.4s;
  }
}

@keyframes typing {
  0%, 100% {
    transform: translateY(0);
    opacity: 0.5;
  }
  50% {
    transform: translateY(-5px);
    opacity: 1;
  }
}

/* 快捷提问区域 */
:deep(.n-space) {
  margin-left: 0 !important;
  
  .n-button {
    background-color: rgba(38, 50, 71, 0.6) !important;
    color: var(--text-light) !important;
    border: 1px solid var(--card-border-dark) !important;
    border-radius: 4px;
    
    &:hover {
      background-color: rgba(74, 111, 164, 0.3) !important;
      border-color: var(--accent-color) !important;
    }
  }
}

/* 输入区域容器 */
div[p="14px"] {
  background-color: rgba(26, 35, 51, 0.5) !important;
  backdrop-filter: blur(5px);
}

/* 选择框和输入框的全局样式 */
:deep(.n-input),
:deep(.n-base-selection) {
  background-color: rgba(38, 50, 71, 0.6) !important;
  border: 1px solid var(--card-border-dark) !important;
  color: var(--text-light) !important;
}

:deep(.n-input__textarea-el),
:deep(.n-input__input-el),
:deep(.n-base-selection-input),
:deep(.n-base-selection__placeholder) {
  color: var(--text-light) !important;
}

:deep(.n-button) {
  background-color: var(--accent-color) !important;
  border: none !important;
  color: white !important;
}

:deep(.n-button:hover) {
  background-color: var(--accent-hover) !important;
}

/* 修改紫色动画相关样式 */
:deep(.navigation-container) .n-button {
  background-color: var(--accent-color) !important;
  border: none !important;
}

:deep(.navigation-container) .n-base-selection {
  background-color: rgba(38, 50, 71, 0.6) !important;
  border: 1px solid var(--card-border-dark) !important;
}

:deep(.n-base-selection-tags__wrapper) {
  background-color: transparent !important;
}

:deep(.n-button--default-type) {
  background-color: rgba(38, 50, 71, 0.6) !important;
  border: 1px solid var(--card-border-dark) !important;
}

:deep(.n-button--default-type:hover) {
  background-color: rgba(74, 111, 164, 0.3) !important;
  border-color: var(--accent-color) !important;
}

:deep(.n-button--default-type:focus) {
  box-shadow: 0 0 0 2px rgba(74, 111, 164, 0.3) !important;
}

/* 禁用选择框和按钮的默认外发光效果 */
:deep(.n-base-selection:hover),
:deep(.n-base-selection--active),
:deep(.n-button:focus) {
  box-shadow: 0 0 0 2px rgba(74, 111, 164, 0.3) !important;
  border-color: var(--accent-color) !important;
}

.hidden-markdown-preview {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  opacity: 0;
  pointer-events: none;
}

// 增强markdown-body样式
:deep(.markdown-body) {
  // 移除默认外边距，确保内容与气泡边缘对齐
  margin: 0;
  padding: 0;
  color: var(--text-light);
  
  p {
    margin-top: 0;
    margin-bottom: 8px;
    
    &:last-child {
      margin-bottom: 0;
    }
  }
  
  // 思考过程样式
  .thinking-process {
    background-color: rgba(58, 86, 126, 0.3);
    border-left: 3px solid var(--accent-color);
    padding: 8px 12px;
    margin: 8px 0;
    border-radius: 0 5px 5px 0;
    font-style: italic;
    color: var(--text-light-secondary);
    position: relative;
    
    &::before {
      content: "思考过程";
      display: block;
      font-weight: bold;
      margin-bottom: 5px;
      color: var(--accent-color);
      font-style: normal;
    }
  }
  
  // 代码块样式
  pre {
    background-color: rgba(0, 0, 0, 0.3);
    padding: 10px;
    border-radius: 5px;
    overflow-x: auto;
    margin: 8px 0;
    border: 1px solid rgba(255, 255, 255, 0.1);
    
    code {
      font-family: Consolas, Monaco, 'Andale Mono', monospace;
      font-size: 13px;
      line-height: 1.4;
      color: #e6eaf0;
    }
  }
  
  // 内联代码样式
  code {
    background-color: rgba(0, 0, 0, 0.2);
    border-radius: 3px;
    padding: 2px 4px;
    font-family: Consolas, Monaco, 'Andale Mono', monospace;
    color: #e6eaf0;
  }
  
  // 列表样式
  ul, ol {
    padding-left: 20px;
    margin: 8px 0;
  }
  
  // 表格样式
  table {
    border-collapse: collapse;
    margin: 10px 0;
    width: 100%;
    
    th, td {
      padding: 6px 10px;
      border: 1px solid rgba(255, 255, 255, 0.1);
    }
    
    th {
      background-color: rgba(0, 0, 0, 0.2);
    }
    
    tr:nth-child(even) {
      background-color: rgba(255, 255, 255, 0.05);
    }
  }
  
  // 图片样式
  img {
    max-width: 100%;
    height: auto;
    margin: 10px 0;
    border-radius: 5px;
  }
  
  // 引用块样式
  blockquote {
    border-left: 4px solid var(--accent-color);
    padding: 0 15px;
    margin: 10px 0;
    color: var(--text-light-secondary);
    background-color: rgba(0, 0, 0, 0.2);
    border-radius: 0 5px 5px 0;
  }
  
  // 水平线样式
  hr {
    height: 1px;
    background-color: rgba(255, 255, 255, 0.1);
    border: none;
    margin: 15px 0;
  }
  
  // 超链接样式
  a {
    color: var(--accent-color);
    text-decoration: none;
    
    &:hover {
      text-decoration: underline;
      color: var(--accent-hover);
    }
  }
}

/* 快捷提问容器 */
div[p="10px 20px"] {
  background-color: rgba(26, 35, 51, 0.5) !important;
  backdrop-filter: blur(5px);
}

/* 发送按钮样式 */
:deep(.n-input-wrapper) {
  .n-button {
    background-color: var(--accent-color) !important;
    border: none !important;
    color: white !important;
    
    &:hover {
      background-color: var(--accent-hover) !important;
    }
  }
}

/* 模型选择下拉框样式 */
:deep(.n-select-menu) {
  background-color: var(--primary-dark) !important;
  border: 1px solid var(--card-border-dark) !important;
  
  .n-base-select-option {
    color: var(--text-light) !important;
    
    &:hover, &.n-base-select-option--pending {
      background-color: rgba(74, 111, 164, 0.3) !important;
    }
    
    &.n-base-select-option--selected {
      color: var(--accent-color) !important;
      background-color: rgba(74, 111, 164, 0.2) !important;
    }
  }
  
  .n-base-select-menu__empty {
    color: var(--text-light-secondary) !important;
  }
  
  .n-base-select-menu__action {
    color: var(--text-light) !important;
    border-top: 1px solid var(--card-border-dark) !important;
  }
}

/* 文本提示框样式 */
:deep(.n-ellipsis) {
  color: var(--text-light) !important;
}

:deep(.wrapper-tooltip-scroller) {
  background-color: var(--primary-dark) !important;
  border: 1px solid var(--card-border-dark) !important;
  color: var(--text-light) !important;
}

/* 修改PromptTag组件样式 */
:deep([class*="text-12"][class*="c-#525252"][class*="bg-#ededed"]) {
  color: var(--text-light) !important;
  background-color: rgba(38, 50, 71, 0.6) !important;
  border: 1px solid var(--card-border-dark) !important;
  
  &:hover {
    background-color: rgba(74, 111, 164, 0.3) !important;
    border-color: var(--accent-color) !important;
    box-shadow: 0 0 0 2px rgba(74, 111, 164, 0.3) !important;
  }
}

.prompt-tag {
  color: var(--text-light);
  background-color: rgba(38, 50, 71, 0.6);
  border: 1px solid var(--card-border-dark);
  
  &:hover {
    background-color: rgba(74, 111, 164, 0.3);
    border-color: var(--accent-color);
  }
}
</style>
