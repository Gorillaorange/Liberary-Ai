<script lang="ts" setup>
import { ref } from 'vue'
import { useAppStore } from '@/store/hooks/useAppStore'
import { useSessionStore } from '@/store/hooks/useSessionStore'
import SessionList from './SessionList.vue'
import { useRouter } from 'vue-router'
import { useMessage, NButton, NDropdown, NDivider, NScrollbar, NEmpty, NSpin } from 'naive-ui'

const appstore = useAppStore()
const sessionStore = useSessionStore()
const router = useRouter()
const message = useMessage()

// 会话列表是否展开
const sessionsExpanded = ref(false)

const toggleSessionsExpanded = () => {
  sessionsExpanded.value = !sessionsExpanded.value
}

const handleCollapse = () => {
  appstore.toggleSidebarCollapsed()
}

const handleCreate = async () => {
  console.log('开始创建新会话')
  try {
    const id = await sessionStore.createNewSession()
    console.log('创建会话成功, id:', id)
    console.log('id类型:', typeof id)
    if (!id) {
      console.log('未获取到有效的会话ID，创建失败')
      return
    }
    console.log('会话已创建，尝试加载会话列表')
    await sessionStore.loadSessions()
    // 创建会话后展开会话列表
    sessionsExpanded.value = true
  } catch (error) {
    console.error('创建会话异常:', error)
  }
}

const toLink = (path: string) => {
  router.push(path)
}

const handleClean = async () => {
  if (confirm('确定要清空所有会话吗？')) {
    // 清理会话
    const success = await sessionStore.clearSessions()
    if (!success) {
      message.error('清空会话失败')
    }
  }
}
</script>

<template>
  <div class="vu__sidebar" :class="{'collapsed': appstore.config.collapsed}">
    <n-button class="vu__sidebar-collapse sidebar-collapse-btn" @click="handleCollapse">
      <svg class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" width="20" height="20">
        <path d="M330.666667 512c0-14.933333 4.266667-29.866667 14.933333-40.533333l234.666667-277.33333399c23.466667-27.733333 64-29.866667 89.6-8.53333301 27.733333 23.466667 29.866667 64 8.53333299 89.6L477.866667 512l200.53333299 236.8c23.466667 27.733333 19.19999999 68.266667-8.53333299 89.6-27.733333 23.466667-68.266667 19.19999999-89.6-8.53333301l-234.666667-277.33333399c-10.666667-10.666667-14.933333-25.6-14.933333-40.533333z" fill="currentColor"/>
      </svg>
    </n-button>
    <aside class="vu__sidebar-aside flex1 flexbox flex-col">
      <div class="vu__aside-head">
        <router-link to="/" class="logo"><i class="iconfont ai-deepseek"></i><span class="fs-14 ff-ab">图书问答系统</span></router-link>
        <div class="btn-create flex-c mt-15" @click="handleCreate"><i class="iconfont ai-newchat fs-20"></i>新建对话</div>
      </div>
      <div class="vu__aside-navlinks flexbox flex-col">
        <div class="section-navitem" @click="toLink('/aihub')">
          <span class="icon flex-c"><icon-compass size="18" /></span>
          <div class="title">推荐书籍</div>
        </div>
        <n-dropdown trigger="hover" :show-arrow="false" position="rt" :popup-offset="15" :content-style="{'min-width': '150px', 'background-color': 'var(--secondary-dark)', 'color': 'var(--text-light)'}">
          <div class="section-navitem">
            <!-- <span class="icon flex-c"><icon-command size="18" /></span>
            <div class="title">AI 技能</div>
            <i class="iconfont ai-arrR c-999 fs-12"></i> -->
          </div> 

        </n-dropdown> 
      </div>
      <n-divider style="margin: 0; border-color: var(--card-border-dark);" />
      <div class="vu__aside-sessions flex1 flexbox flex-col">
        <div class="vu__aside-navlinks">
          <div class="section-navitem plain" @click="toggleSessionsExpanded">
            <span class="icon flex-c"><icon-message size="18" /></span>
            <div class="title">最近对话</div>
            <span class="toggle-icon">
              <i :class="[sessionsExpanded ? 'iconfont ai-arrow-down' : 'iconfont ai-arrow-right']"></i>
            </span>
            <i class="clean iconfont ai-qingli" @click.stop="handleClean"></i>
          </div>
        </div>
        <div class="sessions-container" :class="{'sessions-expanded': sessionsExpanded}">
          <n-scrollbar :outsidestyle="{'height': '100%'}">
            <template v-if="sessionStore.sessions.length">
              <SessionList />
            </template>
            <template v-else>
              <div class="empty-placeholder" v-if="!sessionStore.loading">
                <n-empty description="暂无对话" />
              </div>
              <div class="loading-placeholder" v-else>
                <n-spin />
              </div>
            </template>
          </n-scrollbar>
        </div>
      </div>
      <div class="vu__aside-navlinks" @click="toLink('/setting')">
        <div class="section-navitem">
          <span class="icon flex-c"><icon-settings size="18" /></span>
          <div class="title">设置</div>
        </div>
      </div>
    </aside>
  </div>
</template>

<style lang="scss" scoped>
.vu__sidebar {
  position: relative;
  height: 100%;
  width: 260px;
  transition: all 0.3s;
  
  &.collapsed {
    width: 0;
    
    .vu__sidebar-aside {
      transform: translateX(-100%);
    }
    
    .vu__sidebar-collapse {
      right: -40px;
      
      .icon {
        transform: rotate(180deg);
      }
    }
  }
  
  &-collapse {
    position: absolute;
    top: 10px;
    right: 10px;
    z-index: 10;
    transition: all 0.3s;
    background-color: var(--secondary-dark) !important;
    color: var(--text-light) !important;
    border: 1px solid var(--card-border-dark) !important;
    width: 32px !important;
    height: 32px !important;
    padding: 0 !important;
    display: flex !important;
    align-items: center !important;
    justify-content: center !important;
    border-radius: 6px !important;
    
    .icon {
      transition: transform 0.3s ease;
      fill: currentColor;
    }
    
    &:hover {
      background-color: var(--accent-color) !important;
      border-color: var(--accent-color) !important;
      transform: scale(1.05);
    }
    
    &:active {
      transform: scale(0.95);
    }
  }
  
  &-aside {
    position: relative;
    height: 100%;
    background: rgba(13, 25, 42, 0.7);
    backdrop-filter: blur(10px);
    border-right: 1px solid var(--card-border-dark);
    padding: 15px 0;
    transition: all 0.3s;
  }
}

.vu__aside-head {
  padding: 0 15px;
  margin-bottom: 20px;
  
  .logo {
    display: flex;
    align-items: center;
    font-size: 18px;
    color: var(--text-light);
    text-decoration: none;
    
    i {
      margin-right: 8px;
      font-size: 24px;
    }
  }
  
  .btn-create {
    width: 100%;
    height: 40px;
    background: var(--accent-color);
    color: #fff;
    border-radius: 6px;
    cursor: pointer;
    transition: all 0.2s;
    
    i {
      margin-right: 6px;
    }
    
    &:hover {
      background: var(--accent-hover);
    }
  }
}

.vu__aside-navlinks {
  padding: 0 10px;
  
  .section-navitem {
    display: flex;
    align-items: center;
    padding: 10px;
    margin: 5px 0;
    border-radius: 6px;
    cursor: pointer;
    transition: all 0.2s;
    color: var(--text-light);
    
    &:hover {
      background: rgba(38, 50, 71, 0.6);
    }
    
    &.plain {
      justify-content: space-between;
      
      .clean {
        opacity: 0;
        transition: all 0.2s;
        margin-left: 5px;
      }
      
      .toggle-icon {
        margin-left: auto;
        margin-right: 5px;
        font-size: 12px;
        color: var(--text-light-secondary);
      }
      
      &:hover .clean {
        opacity: 1;
      }
    }
    
    .icon {
      margin-right: 10px;
      width: 20px;
      height: 20px;
      color: var(--text-light);
    }
    
    .title {
      flex: 1;
    }
  }
}

.vu__aside-sessions {
  margin: 10px 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.sessions-container {
  height: 0;
  overflow: hidden;
  transition: height 0.3s ease;
}

.sessions-expanded {
  height: auto;
  max-height: calc(100vh - 250px);
  overflow-y: auto;
}

.empty-placeholder, .loading-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 30px 0;
  color: var(--text-light-secondary);
}

:deep(.n-dropdown-menu) {
  background-color: var(--secondary-dark) !important;
  border: 1px solid var(--card-border-dark) !important;
  
  .dropdown-item {
    padding: 8px 12px;
    color: var(--text-light) !important;
    cursor: pointer;
    
    &:hover {
      background-color: var(--accent-color) !important;
    }
  }
}

:deep(.n-empty) {
  color: var(--text-light-secondary);
}

:deep(.n-spin-body) {
  color: var(--text-light);
}

.flex-c {
  display: flex;
  align-items: center;
  justify-content: center;
}

.flex1 {
  flex: 1;
}

.flexbox {
  display: flex;
}

.flex-col {
  flex-direction: column;
}

.fs-14 {
  font-size: 14px;
}

.fs-20 {
  font-size: 20px;
}

.ff-ab {
  font-family: Arial, sans-serif;
}

.mt-15 {
  margin-top: 15px;
}

.c-999 {
  color: #999;
}

.sidebar {
  background-color: rgba(13, 25, 42, 0.7) !important;
  backdrop-filter: blur(10px);
  border-right: 1px solid var(--card-border-dark);
  transition: all 0.3s ease;
}

.sidebar-header {
  border-bottom: 1px solid var(--card-border-dark);
  
  h2 {
    color: var(--text-light);
  }
}

.sidebar-content {
  .session-list {
    .session-item {
      border-bottom: 1px solid rgba(255, 255, 255, 0.05);
      color: var(--text-light);
      
      &:hover {
        background-color: rgba(38, 50, 71, 0.6);
      }
      
      &.active {
        background-color: rgba(74, 111, 164, 0.3);
        border-left: 3px solid var(--accent-color);
      }
      
      .session-time {
        color: var(--text-light-secondary);
      }
    }
  }
}

.sidebar-footer {
  border-top: 1px solid var(--card-border-dark);
  background-color: rgba(26, 35, 51, 0.7);
  
  .create-session-button {
    background-color: var(--accent-color);
    color: white;
    
    &:hover {
      background-color: var(--accent-hover);
    }
  }
}
</style> 