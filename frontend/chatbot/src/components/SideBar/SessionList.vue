<script lang="ts" setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useSessionStore } from '@/store/hooks/useSessionStore'

const sessionStore = useSessionStore()

// 会话操作相关数据
const activeMenuSessionId = ref(null) // 当前激活菜单的会话ID
const deleteSessionId = ref(null)
const deleteDialogVisible = ref(false)
const editSessionId = ref(null)
const editSessionTitle = ref('')
const editDialogVisible = ref(false)

// 创建新会话
const createNewSession = async () => {
  try {
    await sessionStore.createNewSession('新对话')
  } catch (error) {
    console.error('创建会话失败:', error)
  }
}

// 处理会话点击
const handleSessionClick = (sessionId) => {
  sessionStore.setCurrentSession(sessionId)
}

// 打开会话菜单
const toggleSessionMenu = (event, sessionId) => {
  event.stopPropagation()
  if (activeMenuSessionId.value === sessionId) {
    // 如果点击的是已经激活的菜单，则关闭
    activeMenuSessionId.value = null
  } else {
    // 否则打开当前会话的菜单
    activeMenuSessionId.value = sessionId
  }
}

// 关闭菜单
const closeMenu = () => {
  activeMenuSessionId.value = null
}

// 处理会话编辑
const handleSessionEdit = (event, sessionId, title) => {
  event.stopPropagation()
  closeMenu()
  editSessionId.value = sessionId
  editSessionTitle.value = title || '新对话'
  editDialogVisible.value = true
}

// 处理会话删除
const handleSessionRemove = (event, sessionId) => {
  event.stopPropagation()
  closeMenu()
  deleteSessionId.value = sessionId
  deleteDialogVisible.value = true
}

// 更新会话标题
const updateSessionTitle = async () => {
  if (editSessionId.value && editSessionTitle.value.trim()) {
    try {
      console.log(`准备更新会话(${editSessionId.value})标题为: ${editSessionTitle.value}`)
      // 使用store中可用的方法
      const success = await sessionStore.editSession(editSessionId.value, editSessionTitle.value)
      
      if (success) {
        console.log('会话标题更新成功')
        editDialogVisible.value = false
      } else {
        console.error('会话标题更新失败')
        alert('更新会话标题失败，请重试')
      }
    } catch (error) {
      console.error('更新会话标题异常:', error)
      alert('更新会话标题失败，请重试')
    }
  } else {
    console.warn('标题为空，不更新')
    alert('会话标题不能为空')
  }
}

// 确认删除会话
const deleteSession = async () => {
  if (deleteSessionId.value) {
    try {
      console.log(`准备删除会话: ${deleteSessionId.value}`)
      // 使用store中可用的方法
      const success = await sessionStore.removeSession(deleteSessionId.value)
      
      if (success) {
        console.log('会话删除成功')
        deleteDialogVisible.value = false
      } else {
        console.error('会话删除失败')
        alert('删除会话失败，请重试')
      }
    } catch (error) {
      console.error('删除会话异常:', error)
      alert('删除会话失败，请重试')
    }
  } else {
    console.warn('没有选择要删除的会话')
  }
}

// 取消编辑
const cancelEdit = () => {
  editDialogVisible.value = false
}

// 取消删除
const cancelDelete = () => {
  deleteDialogVisible.value = false
}

// 点击页面其他区域关闭菜单
const handleDocumentClick = (event) => {
  if (activeMenuSessionId.value !== null) {
    closeMenu()
  }
}

// 在组件挂载时添加全局点击事件监听
onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
})

// 在组件卸载前移除全局点击事件监听
onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
})

// 格式化时间
const formatTime = (date) => {
  if (!date) return ''
  
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  
  // 小于24小时显示HH:mm
  if (diff < 24 * 60 * 60 * 1000) {
    return `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
  }
  
  // 一周内显示星期几
  if (diff < 7 * 24 * 60 * 60 * 1000) {
    const days = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
    return days[date.getDay()]
  }
  
  // 更早显示年月日
  return `${date.getFullYear()}-${(date.getMonth() + 1).toString().padStart(2, '0')}-${date.getDate().toString().padStart(2, '0')}`
}
</script>

<template>
  <div class="session-list-container">
    <div class="scrollable-container">
      <div v-if="sessionStore.loading" class="loading-container">
        <div class="loading-spinner"></div>
        <span>加载中...</span>
      </div>
      
      <div v-else-if="sessionStore.sessions.length === 0" class="empty-container">
        <span>暂无会话记录</span>
      </div>
      
      <ul v-else class="session-items">
        <li
          v-for="session in sessionStore.sessions"
          :key="session.id"
          :class="{ active: session.id === sessionStore.currentSessionId }"
          @click="handleSessionClick(session.id)"
        >
          <div class="session-item-content">
            <div class="session-title">
              <span>{{ session.title || '新对话' }}</span>
            </div>
            <div class="session-preview" v-if="session.lastMessagePreview">
              {{ session.lastMessagePreview }}
            </div>
            <div class="session-time" v-if="session.createdAt">
              {{ formatTime(new Date(session.createdAt)) }}
            </div>
          </div>
          
          <div class="session-menu">
            <button class="menu-button" @click.stop="toggleSessionMenu($event, session.id)">
              <span class="menu-dots">···</span>
            </button>
            
            <div class="menu-dropdown" v-if="activeMenuSessionId === session.id">
              <div class="menu-item" @click.stop="handleSessionEdit($event, session.id, session.title)">
                编辑会话
              </div>
              <div class="menu-item delete" @click.stop="handleSessionRemove($event, session.id)">
                删除会话
              </div>
            </div>
          </div>
        </li>
      </ul>
    </div>

    <!-- 删除确认对话框 -->
    <div v-if="deleteDialogVisible" class="dialog-overlay">
      <div class="dialog-container">
        <div class="dialog-header">确认删除</div>
        <div class="dialog-content">确定要删除这个会话吗？此操作无法撤销。</div>
        <div class="dialog-footer">
          <button class="dialog-button cancel" @click="cancelDelete">取消</button>
          <button class="dialog-button danger" @click="deleteSession">确认删除</button>
        </div>
      </div>
    </div>

    <!-- 编辑会话标题对话框 -->
    <div v-if="editDialogVisible" class="dialog-overlay">
      <div class="dialog-container">
        <div class="dialog-header">编辑会话标题</div>
        <div class="dialog-content">
          <input class="dialog-input" v-model="editSessionTitle" placeholder="请输入新标题">
        </div>
        <div class="dialog-footer">
          <button class="dialog-button cancel" @click="cancelEdit">取消</button>
          <button class="dialog-button primary" @click="updateSessionTitle">确认</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.session-list-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;
}

.scrollable-container {
  flex: 1;
  overflow-y: auto;
}

.loading-container, .empty-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
  color: var(--text-light-secondary);
  
  .loading-spinner {
    border: 3px solid rgba(255,255,255,0.1);
    border-top: 3px solid var(--accent-color);
    border-radius: 50%;
    width: 20px;
    height: 20px;
    animation: spin 1s linear infinite;
    margin-bottom: 10px;
  }
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.session-items {
  list-style: none;
  padding: 0;
  margin: 0;
  
  li {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 15px;
    border-bottom: 1px solid var(--card-border-dark);
    cursor: pointer;
    transition: background-color 0.2s;
    position: relative;
    
    &:hover {
      background-color: rgba(38, 50, 71, 0.6);
    }
    
    &.active {
      background-color: rgba(74, 111, 164, 0.3);
      border-left: 3px solid var(--accent-color);
    }
  }
}

.session-item-content {
  flex: 1;
  min-width: 0;
  
  .session-title {
    font-weight: 500;
    margin-bottom: 4px;
    color: var(--text-light);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  
  .session-preview {
    font-size: 12px;
    color: var(--text-light-secondary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    margin-bottom: 4px;
  }
  
  .session-time {
    font-size: 11px;
    color: var(--text-light-secondary);
  }
}

.session-menu {
  position: relative;
  
  .menu-button {
    background: none;
    border: none;
    padding: 5px;
    cursor: pointer;
    color: var(--text-light-secondary);
    transition: color 0.2s;
    
    &:hover {
      color: var(--text-light);
    }
    
    .menu-dots {
      font-size: 16px;
      display: block;
      transform: rotate(90deg);
      font-weight: bold;
    }
  }
  
  .menu-dropdown {
    position: absolute;
    top: 100%;
    right: 0;
    width: 120px;
    background-color: var(--secondary-dark);
    border: 1px solid var(--card-border-dark);
    border-radius: 4px;
    box-shadow: 0 2px 8px rgba(0,0,0,0.15);
    z-index: 10;
    
    .menu-item {
      padding: 8px 12px;
      font-size: 13px;
      color: var(--text-light);
      cursor: pointer;
      transition: background-color 0.2s;
      
      &:hover {
        background-color: rgba(74, 111, 164, 0.3);
      }
      
      &.delete {
        color: #ff4d4f;
        
        &:hover {
          background-color: rgba(255, 77, 79, 0.1);
        }
      }
    }
  }
}

.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.dialog-container {
  background-color: var(--primary-dark);
  border-radius: 8px;
  width: 350px;
  box-shadow: 0 3px 15px rgba(0,0,0,0.2);
  border: 1px solid var(--card-border-dark);
}

.dialog-header {
  padding: 15px;
  border-bottom: 1px solid var(--card-border-dark);
  font-weight: 500;
  color: var(--text-light);
}

.dialog-content {
  padding: 20px 15px;
  color: var(--text-light);
  
  .dialog-input {
    width: 100%;
    padding: 8px 12px;
    border: 1px solid var(--card-border-dark);
    border-radius: 4px;
    background-color: var(--secondary-dark);
    color: var(--text-light);
    
    &:focus {
      border-color: var(--accent-color);
      outline: none;
    }
  }
}

.dialog-footer {
  padding: 15px;
  border-top: 1px solid var(--card-border-dark);
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.dialog-button {
  padding: 6px 12px;
  border-radius: 4px;
  border: none;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
  
  &.cancel {
    background-color: transparent;
    border: 1px solid var(--card-border-dark);
    color: var(--text-light-secondary);
    
    &:hover {
      background-color: rgba(255,255,255,0.05);
    }
  }
  
  &.primary {
    background-color: var(--accent-color);
    color: white;
    
    &:hover {
      background-color: var(--accent-hover);
    }
  }
  
  &.danger {
    background-color: rgba(220, 53, 69, 0.8);
    color: white;
    
    &:hover {
      background-color: rgba(220, 53, 69, 1);
    }
  }
}
</style> 