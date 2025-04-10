<script lang="tsx" setup>
import { renderMarkdownText } from './plugins/markdown'
import { computed, ref, watch, onMounted } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/atom-one-dark.css'
import { useThemeVars } from 'naive-ui'

import type { CrossTransformFunction, TransformFunction } from './models'
import { defaultMockModelName } from './models'

interface Props {
  reader: ReadableStreamDefaultReader<Uint8Array> | null | undefined
  model: string | null| undefined
  transformStreamFn: TransformFunction | null | undefined
  text: string
  inversion: boolean
}

const props = withDefaults(
  defineProps<Props>(),
  {
    reader: null,
    inversion: false
  }
)


// 定义响应式变量
const displayText = ref('')
const textBuffer = ref<string>('')
const readerLoading = ref(false)

// 添加调试计数器
const debugCounter = ref(0)

const isAbort = ref(false)

const isCompleted = ref(false)

const emit = defineEmits([
  'failed',
  'completed',
  'update:reader'
])


const refWrapperContent = ref<HTMLElement>()

let typingAnimationFrame: number | null = null

const renderedMarkdown = computed(() => {
  return renderMarkdownText(displayText.value)
})

// 接口响应是否正则排队等待
const waitingForQueue = ref(false)

const WaitTextRender = defineComponent({
  render() {
    return (
      <n-empty
        size="large"
        class="font-bold [&_.n-empty\_\_icon]:flex [&_.n-empty\_\_icon]:justify-center"
      >
        {{
          default: () => (
            <div
              whitespace-break-spaces
              text-center
            >请求排队处理中，请耐心等待...</div>
          ),
          icon: () => (
            <n-icon class="text-30">
              <div class="i-svg-spinners:clock"></div>
            </n-icon>
          )
        }}
      </n-empty>
    )
  }
})

const abortReader = () => {
  if (props.reader) {
    props.reader.cancel()
  }

  isAbort.value = true
  readIsOver.value = false
  emit('update:reader', null)
  initializeEnd()
  isCompleted.value = true
}

const resetStatus = () => {
  isAbort.value = false
  isCompleted.value = false
  readIsOver.value = false

  emit('update:reader', null)

  initializeEnd()
  displayText.value = ''
  textBuffer.value = ''
  readerLoading.value = false
  waitingForQueue.value = false
  if (typingAnimationFrame) {
    cancelAnimationFrame(typingAnimationFrame)
    typingAnimationFrame = null
  }
}

/**
 * 检查是否有实际内容
 */
function hasActualContent(html) {
  const text = html.replace(/<[^>]*>/g, '')
  return /\S/.test(text)
}

const showCopy = computed(() => {
  if (!isCompleted.value) return false

  if (hasActualContent(displayText.value)) {
    return true
  }
  return false
})

const renderedContent = computed(() => {
  // 添加调试日志
  console.log("渲染内容，displayText:", displayText.value);
  // 在 renderedMarkdown 末尾插入光标标记
  return `${ renderedMarkdown.value }`
})


const initialized = ref(false)

const initializeStart = () => {
  initialized.value = true
}

const initializeEnd = () => {
  initialized.value = false
}

/**
 * reader 读取是否结束
 */
const readIsOver = ref(false)

// 在script顶部添加回调函数的ref
const onTextUpdateCallback = ref<((text: string) => void) | null>(null)
const onCompleteCallback = ref<(() => void) | null>(null)

// 添加setter方法暴露给外部组件
const setOnTextUpdate = (callback: (text: string) => void) => {
  console.log("设置文本更新回调", callback);
  onTextUpdateCallback.value = callback;
}

const setOnComplete = (callback: () => void) => {
  console.log("设置完成回调", callback);
  onCompleteCallback.value = callback;
}

const readTextStream = async () => {
  if (!props.reader) {
    console.log("没有提供reader，无法读取流");
    return;
  }

  console.log("开始读取文本流");
  const textDecoder = new TextDecoder('utf-8')
  readerLoading.value = true

  while (true) {
    if (isAbort.value) {
      console.log("流处理被中止");
      break
    }
    try {
      if (!props.reader) {
        console.log("reader为null，结束流读取");
        readIsOver.value = true
        break
      }
      
      // 读取流内容
      const readResult = await props.reader.read()
      const { value, done } = readResult
      
      console.log("流读取结果:", { 
        done, 
        hasValue: !!value, 
        valueType: value ? typeof value : 'undefined',
        content: value ? (typeof value === 'string' ? (value as string).slice(0, 50) + '...' : '非文本数据') : '无数据' 
      });
      
      if (!props.reader) {
        console.log("reader已变为null，结束流读取");
        readIsOver.value = true
        break
      }
      
      if (done) {
        console.log("流读取完成(done=true)，标记readIsOver=true");
        readIsOver.value = true
        break
      }

      const transformer = props.transformStreamFn as CrossTransformFunction
      if (!transformer) {
        console.error("没有找到转换函数");
        readIsOver.value = true
        break
      }

      console.log("调用transformer处理数据", value);
      let stream;
      try {
        stream = transformer(value, textDecoder)
        console.log("transformer返回结果:", stream);
      } catch (transformError) {
        console.error("转换过程出错:", transformError);
        // 尝试直接将value当作字符串使用
        if (typeof value === 'string' || value instanceof Uint8Array) {
          const content = typeof value === 'string' ? value : textDecoder.decode(value);
          stream = { content };
          console.log("回退处理结果:", stream);
        } else {
          console.error("无法处理的数据类型:", typeof value);
          continue;
        }
      }
      
      // 检查是否已完成
      if (stream && stream.done) {
        console.log("流已完成(stream.done=true)");
        readIsOver.value = true
        break
      }

      if (stream && stream.isWaitQueuing) {
        console.log("等待队列中");
        waitingForQueue.value = stream.isWaitQueuing
      }
      
      // 检查内容是否存在
      if (stream && stream.content !== undefined && stream.content !== null) {
        // 简化内容日志记录，避免类型错误
        console.log("接收到内容类型:", typeof stream.content);
        
        waitingForQueue.value = false
        // 确保内容是字符串类型
        if (typeof stream.content === 'string') {
          textBuffer.value += stream.content
        } else {
          // 尝试转换非字符串内容
          try {
            textBuffer.value += String(stream.content)
          } catch (conversionError) {
            console.error("内容转换失败:", conversionError);
          }
        }
      } else {
        console.log("没有接收到内容");
      }

      if (typingAnimationFrame === null) {
        console.log("启动打字动画");
        showText()
      }
    } catch (error) {
      console.error("流读取错误:", error);
      readIsOver.value = true
      emit('failed', error)
      resetStatus()
      break
    } finally {
      initializeEnd()
    }
  }
  
  // 如果读取已结束，但仍有缓冲的内容未显示，确保播放动画
  if (readIsOver.value && textBuffer.value.length > 0 && typingAnimationFrame === null) {
    console.log("流读取结束但有缓冲内容，启动显示动画");
    showText()
  } else if (readIsOver.value) {
    // 如果流结束但没有新内容，可能是出错了或已完成，通知完成
    console.log("流结束，发送完成信号");
    if (textBuffer.value.length === 0 && displayText.value.length === 0) {
      // 如果完全没有内容，可能是出错了
      emit('failed', new Error("未收到任何内容"));
    } else {
      // 如果已经有显示内容，则正常完成
      emit('completed');
    }
    readerLoading.value = false;
    isCompleted.value = true;
    waitingForQueue.value = false;
  }
}

const scrollToBottom = async () => {
  await nextTick()
  if (!refWrapperContent.value) return

  refWrapperContent.value.scrollTop = refWrapperContent.value.scrollHeight
}
const scrollToBottomByThreshold = async () => {
  if (!refWrapperContent.value) return

  const threshold = 100
  const distanceToBottom = refWrapperContent.value.scrollHeight - refWrapperContent.value.scrollTop - refWrapperContent.value.clientHeight
  if (distanceToBottom <= threshold) {
    scrollToBottom()
  }
}

const scrollToBottomIfAtBottom = async () => {
  // TODO: 需要同时支持手动向上滚动
  scrollToBottomByThreshold()
}

/**
 * 读取 buffer 内容，逐字追加到 displayText
 */
const runReadBuffer = (readCallback = () => {}, endCallback = () => {}) => {
  if (textBuffer.value.length > 0) {
    const nextChunk = textBuffer.value.substring(0, 10)
    displayText.value += nextChunk
    textBuffer.value = textBuffer.value.substring(10)
    
    // 触发内容更新回调
    if (onTextUpdateCallback.value) {
      console.log("调用文本更新回调，当前文本长度：", displayText.value.length);
      onTextUpdateCallback.value(displayText.value)
    } else {
      console.warn("无文本更新回调");
    }
    
    readCallback()
  } else {
    endCallback()
  }
}

const showText = () => {
  // 更新调试计数器
  debugCounter.value++
  console.log(`调试: showText被调用(${debugCounter.value})`, { 
    bufferLength: textBuffer.value.length,
    displayLength: displayText.value.length,
    readIsOver: readIsOver.value
  });

  if (isAbort.value && typingAnimationFrame) {
    cancelAnimationFrame(typingAnimationFrame)
    typingAnimationFrame = null
    readerLoading.value = false
    return
  }

  // 若 reader 还没结束，则保持打字行为
  if (!readIsOver.value) {
    runReadBuffer()
    typingAnimationFrame = requestAnimationFrame(showText)
  } else {
    // 读取剩余的 buffer
    runReadBuffer(
      () => {
        typingAnimationFrame = requestAnimationFrame(showText)
      },
      () => {
        console.log("生成完毕，清理状态");
        window.$ModalNotification.success({
          title: '生成完毕',
          duration: 1500
        })
        
        // 确保reader被清空
        emit('update:reader', null)
        
        // 触发完成事件
        emit('completed')
        
        // 调用完成回调
        if (onCompleteCallback.value) {
          console.log("调用完成回调");
          onCompleteCallback.value()
        } else {
          console.warn("无完成回调");
        }
        
        // 重置所有状态标志
        typingAnimationFrame = null
        readerLoading.value = false
        isCompleted.value = true
        waitingForQueue.value = false
        
        // 触发一次额外的滚动，确保内容完全显示
        nextTick(() => {
          scrollToBottom()
        })
      }
    )
  }

  // 根据接近底部自动滚动
  scrollToBottomIfAtBottom()
}

watch(
  () => props.reader,
  () => {
    if (props.reader) {
      readTextStream()
    }
  },
  {
    immediate: true,
    deep: true
  }
)


onUnmounted(() => {
  resetStatus()
})

// 修改defineExpose
defineExpose({
  abortReader,
  resetStatus,
  initializeStart,
  initializeEnd,
  onTextUpdate: setOnTextUpdate,
  onComplete: setOnComplete,
  get renderedContent() {
    return renderedContent.value
  }
})

const showLoading = computed(() => {
  if (initialized.value) {
    return true
  }

  if (!props.reader) {
    return false
  }

  if (!readerLoading) {
    return false
  }
  if (displayText.value) {
    return false
  }

  return false
})

const refClipBoard = ref()
const handlePassClip = () => {
  if (refClipBoard.value) {
    refClipBoard.value.copyText()
  }
}

const emptyPlaceholder = computed(() => {
  return defaultMockModelName === props.model
    ? '当前为模拟环境\n随便问一个问题，我才会消失 ~'
    : '问一个问题，我才会消失 ~'
})

const md = new MarkdownIt({
  linkify: true,
  highlight(code, language) {
    try {
      if (language && hljs.getLanguage(language)) {
        const result = hljs.highlight(code, { language }).value
        return `<pre class="hljs"><code>${result}</code></pre>`
      }
    }
    catch (err) {}
    return `<pre class="hljs"><code>${md.utils.escapeHtml(code)}</code></pre>`
  },
})

// 添加自定义渲染规则
md.renderer.rules.text = function(tokens, idx) {
  const content = tokens[idx].content
  // 移除可能存在的result标记
  return content.replace(/^\s*{\s*"result"\s*:\s*"(.+?)"\s*}\s*$/, '$1')
}

const theme = useThemeVars()
// const { isMobile } = useBasicLayout()
// 直接定义一个isMobile变量
const isMobile = computed(() => {
  // 如果运行在浏览器环境，则检查窗口宽度
  if (typeof window !== 'undefined') {
    return window.innerWidth <= 768
  }
  // 默认返回false
  return false
})

const wrapClass = computed(() => {
  return [
    'markdown-body',
    {
      'markdown-body--dark': props.inversion,
    },
  ]
})

const markdownContent = computed(() => {
  const text = props.text.trim()
  // 如果文本是JSON格式，尝试提取内容
  if (text.startsWith('{') && text.endsWith('}')) {
    try {
      const jsonObj = JSON.parse(text)
      if (jsonObj.result)
        return md.render(jsonObj.result)
      if (jsonObj.content)
        return md.render(jsonObj.content)
    }
    catch (err) {}
  }
  // 如果文本以data:开头，移除这个前缀
  if (text.startsWith('data: '))
    return md.render(text.substring(6))
  return md.render(text)
})
</script>

<template>
  <n-spin
    relative
    flex="1 ~"
    min-h-0
    w-full
    h-full
    content-class="w-full h-full flex"
    :show="showLoading"
    :rotate="false"
    class="bg-#fff:30"
    :style="{
      '--n-opacity-spinning': '0.3'
    }"
  >
    <transition name="fade">
      <n-float-button
        v-if="showCopy"
        position="absolute"
        :top="30"
        :right="30"
        color
        class="c-warning bg-#fff/80 hover:bg-#fff/90 transition-all-200 z-2"
        @click="handlePassClip()"
      >
        <clip-board
          ref="refClipBoard"
          :auto-color="false"
          no-copy
          :text="displayText"
        />
      </n-float-button>
    </transition>
    <template #icon>
      <div class="i-svg-spinners:3-dots-rotate"></div>
    </template>
    <!-- b="~ solid #ddd" -->
    <div
      flex="1 ~"
      min-w-0
      min-h-0
      :class="[
        reader
          ? ''
          : 'justify-center items-center'
      ]"
    >
      <div
        text-16
        class="w-full h-full overflow-hidden"
        :class="[
          !displayText && 'flex items-center justify-center'
        ]"
      >
        <WaitTextRender
          v-if="waitingForQueue && !displayText"
        />
        <template v-else>
          <n-empty
            v-if="!displayText"
            size="large"
            class="font-bold"
          >
            <div
              whitespace-break-spaces
              text-center
              v-html="emptyPlaceholder"
            ></div>
            <template #icon>
              <n-icon>
                <div class="i-hugeicons:ai-chat-02"></div>
              </n-icon>
            </template>
          </n-empty>
          <div
            v-else
            ref="refWrapperContent"
            text-16
            class="w-full h-full overflow-y-auto"
            p-24px
          >
            <div
              class="markdown-wrapper"
              v-html="renderedContent"
            ></div>
            <WaitTextRender
              v-if="waitingForQueue"
            />
            <div
              v-if="readerLoading"
              size-24
              class="i-svg-spinners:pulse-3"
            ></div>
          </div>
        </template>
      </div>
    </div>
  </n-spin>
</template>

<style lang="scss">
.markdown-wrapper {

  h1 {
    font-size: 2em;
  }

  h2 {
    font-size: 1.5em;
  }

  h3 {
    font-size: 1.25em;
  }

  h4 {
    font-size: 1em;
  }

  h5 {
    font-size: 0.875em;
  }

  h6 {
    font-size: 0.85em;
  }

  h1,h2,h3,h4,h5,h6 {
    margin: 0 auto;
    line-height: 1.25;
  }

  & ul,ol {
    padding-left: 1.5em;
    line-height: 0.8;
  }

  & ul,li,ol {
    list-style-position: outside;
    white-space: normal;
  }

  li {
    line-height: 1.7;

    & code {
      --at-apply: 'bg-#e5e5e5';
      --at-apply: whitespace-pre m-2px px-6px py-2px rounded-5px;
    }
  }

  ol ol {
    padding-left: 20px;
  }

  ul ul {
    padding-left: 20px;
  }

  hr {
    margin: 16px 0;
  }

  a {
    color: $color-default;
    font-weight: bolder;
    text-decoration: underline;
    padding: 0 3px;
  }

  p {
    line-height: 1.4;

    & > code {
      --at-apply: 'bg-#e5e5e5';
      --at-apply: whitespace-pre mx-4px px-6px py-3px rounded-5px;
    }


    img {
      display: inline-block;
    }
  }

  li > p {
    line-height: 2
  }

  blockquote {
    padding: 10px;
    margin: 20px 0;
    border-left: 5px solid #ccc;
    background-color: #f9f9f9;
    color: #555;

    & > p {
      margin: 0;
    }
  }

  .katex {
    --at-apply: c-primary;
  }

  kbd {
    --at-apply: inline-block align-middle p-0.1em p-0.3em;
    --at-apply: bg-#fcfcfc text-#555;
    --at-apply: border border-solid border-#ccc border-b-#bbb;
    --at-apply: rounded-0.2em shadow-[inset_0_-1px_0_#bbb] text-0.9em;
  }

  table {
    --at-apply: w-fit border-collapse my-16;
  }

  th, td {
    --at-apply: p-7 text-left border border-solid border-#ccc;
  }

  th {
    --at-apply: bg-#f2f2f2 font-bold;
  }

  tr:nth-child(even) {
    --at-apply: bg-#f9f9f9;
  }

  tr:hover {
    --at-apply: bg-#f1f1f1;
  }

  // Deepseek 深度思考 Wrapper

  .think-wrapper {
    --at-apply: pl-13 text-14 c-#8b8b8b;
    --at-apply: b-l-2 b-l-solid b-#e5e5e5;

    p {
      --at-apply: line-height-26;
    }
  }
}
</style>
