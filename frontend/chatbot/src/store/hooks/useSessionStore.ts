import { defineStore } from 'pinia'
import { store } from '@/store'
import { ref, computed } from 'vue'
import axios from 'axios'
import { API_BASE_URL } from '@/config'

export interface ChatMessage {
  id: string
  role: string
  content: string
  createTime: string
  sessionId: string
}

export interface Session {
  id: string
  title: string
  createdAt: string
  lastMessagePreview?: string
}

export const useSessionStore = defineStore('session-store', () => {
  const sessions = ref<Session[]>([])
  const currentSessionId = ref<string>('')
  const currentMessages = ref<ChatMessage[]>([])
  const loading = ref(false)
  const messageLoading = ref(false)

  // 从本地存储恢复会话ID
  const initialize = async () => {
    const savedSessionId = localStorage.getItem('currentSessionId')
    if (savedSessionId) {
      currentSessionId.value = savedSessionId
      await loadSessions()
      if (currentSessionId.value) {
        await loadSessionMessages(currentSessionId.value)
      }
    } else {
      await loadSessions()
    }
  }

  // 加载所有会话
  const loadSessions = async () => {
    loading.value = true
    
    try {
      console.log('开始加载会话列表...')
      console.log('使用的API基础URL:', API_BASE_URL)
      
      // 为了调试，获取并打印token
      const token = localStorage.getItem('token')
      console.log('当前token:', token?.substring(0, 15) + '...')
      
      // 确保token格式正确
      const tokenToUse = token && !token.startsWith('Bearer ') ? `Bearer ${token}` : token
      console.log('格式化后的token格式:', tokenToUse?.substring(0, 20) + '...')
      
      // 使用正确的API地址
      const apiUrl = `${API_BASE_URL}/api/sessions`
      console.log('请求URL:', apiUrl)
      
      const response = await axios.get(apiUrl, {
        headers: {
          Authorization: tokenToUse,
          'Content-Type': 'application/json'
        }
      })
      
      console.log('会话列表响应状态:', response.status)
      console.log('会话列表响应:', response.data)
      
      // 检查响应数据
      if (response.data) {
        // 将响应数据转换为会话数组
        sessions.value = Array.isArray(response.data) 
          ? response.data.map(session => ({
              id: session.id,
              title: session.title || '新对话',
              createdAt: session.createdAt || new Date().toISOString(),
              lastMessagePreview: session.lastMessagePreview || ''
            }))
          : []
          
        console.log('处理后的会话数据:', sessions.value)
        
        // 检查当前选中的会话是否存在于加载的会话列表中
        const sessionExists = sessions.value.some(s => s.id === currentSessionId.value)
        if (!sessionExists) {
          // 如果不存在，清空当前会话ID
          currentSessionId.value = ''
          currentMessages.value = []
          
          // 如果有会话，选择第一个
          if (sessions.value.length > 0) {
            setCurrentSession(sessions.value[0].id)
          }
        }
      } else {
        console.warn('响应中没有会话数据')
        sessions.value = []
      }
    } catch (error) {
      console.error('加载会话失败:', error)
      sessions.value = []
    } finally {
      loading.value = false
    }
  }

  // 创建新会话
  const createNewSession = async (title: string = '新对话') => {
    try {
      const token = localStorage.getItem('token')
      
      console.log('获取到的token:', token)
      
      if (!token) {
        console.log('没有找到token，无法创建会话')
        return null
      }
      
      // 确保token格式正确
      const tokenToUse = token.startsWith('Bearer ') ? token : `Bearer ${token}`
      console.log('格式化后的token:', tokenToUse)
      
      // 使用正确的API地址
      const apiUrl = `${API_BASE_URL}/api/sessions`
      console.log(`准备发送请求到: ${apiUrl}?title=${encodeURIComponent(title)}`)
      
      // 简化为只使用title参数，userId应从JWT中获取
      const requestUrl = `${apiUrl}?title=${encodeURIComponent(title)}`
      
      console.log('最终请求URL:', requestUrl)
      
      const response = await axios.post(
        requestUrl, 
        null, // 空请求体
        {
          headers: {
            Authorization: tokenToUse,
            'Content-Type': 'application/json'
          }
        }
      )
      
      console.log('会话创建响应:', response)
      console.log('响应状态:', response.status)
      console.log('响应数据:', JSON.stringify(response.data))
      
      // 检查响应数据是否为空或null
      if (!response.data) {
        console.log('响应数据为空，无法创建会话')
        return null
      }
      
      // 尝试解析响应数据
      let sessionData = response.data
      
      console.log('处理后的会话数据:', sessionData)
      console.log('会话ID:', sessionData?.id)
      
      if (sessionData && sessionData.id) {
        console.log('响应中的会话ID类型:', typeof sessionData.id)
        console.log('响应中的会话ID值:', sessionData.id)
        
        const newSession = {
          id: sessionData.id,
          title: sessionData.title || '新对话',
          createdAt: sessionData.createdAt || new Date(),
          lastMessagePreview: sessionData.lastMessagePreview || ''
        }
        
        console.log('创建的新会话:', newSession)
        sessions.value.unshift(newSession)
        setCurrentSession(newSession.id)
        return newSession.id
      }
      console.log('未找到有效的会话ID，创建会话失败')
      return null
    } catch (error: any) {
      console.error('创建会话失败:', error)
      console.error('错误详情:', error.response ? JSON.stringify(error.response.data) : '无响应数据')
      console.error('错误状态:', error.response ? error.response.status : '未知状态')
      return null
    }
  }

  // 设置当前会话
  const setCurrentSession = async (id: string) => {
    if (id === currentSessionId.value) return
    
    currentSessionId.value = id
    localStorage.setItem('currentSessionId', id)
    
    // 加载会话消息
    if (id) {
      await loadSessionMessages(id)
    } else {
      currentMessages.value = []
    }
  }

  // 加载某个会话的消息
  const loadSessionMessages = async (sessionId) => {
    if (!sessionId) {
      console.log('没有提供会话ID，无法加载消息')
      currentMessages.value = []
      return
    }
    
    messageLoading.value = true
    
    try {
      console.log(`开始加载会话 ${sessionId} 的消息...`)
      
      // 为了调试，获取并打印token
      const token = localStorage.getItem('token')
      console.log('当前token:', token?.substring(0, 15) + '...')
      
      // 确保token格式正确
      const tokenToUse = token && !token.startsWith('Bearer ') ? `Bearer ${token}` : token
      
      // 使用正确的API地址
      const apiUrl = `${API_BASE_URL}/api/sessions/${sessionId}/messages`
      console.log('请求URL:', apiUrl)
      
      const response = await axios.get(apiUrl, {
        headers: {
          Authorization: tokenToUse,
          'Content-Type': 'application/json'
        }
      })
      
      console.log('会话消息响应状态:', response.status)
      console.log('会话消息响应:', response.data)
      
      // 检查响应数据
      if (response.data) {
        // 确保消息是数组格式
        currentMessages.value = Array.isArray(response.data) ? response.data : []
        console.log('处理后的消息数据:', currentMessages.value)
      } else {
        console.warn('响应中没有消息数据')
        currentMessages.value = []
      }
    } catch (error) {
      console.error('加载会话消息失败:', error)
      currentMessages.value = []
    } finally {
      messageLoading.value = false
    }
  }

  // 添加消息
  const addMessage = async (sessionId: string, role: string, content: string) => {
    if (!sessionId) return null
    
    try {
      const token = localStorage.getItem('token')
      if (!token) {
        return null
      }
      
      console.log('正在添加消息:', sessionId, role, content)
      
      const response = await axios.post(
        `${API_BASE_URL}/api/sessions/${sessionId}/messages`,
        { role, content },
        {
          headers: {
            Authorization: `Bearer ${token}`
          }
        }
      )
      
      console.log('消息添加响应:', response.data)
      
      if (response.data) {
        const messageData = response.data.data || response.data
        // 如果是当前会话，添加到消息列表
        if (sessionId === currentSessionId.value) {
          currentMessages.value.push(messageData)
        }
        
        // 更新会话预览
        const sessionIndex = sessions.value.findIndex(s => s.id === sessionId)
        if (sessionIndex !== -1) {
          const preview = content.length > 30 ? content.substring(0, 30) + '...' : content
          sessions.value[sessionIndex].lastMessagePreview = preview
        }
        
        return messageData.id
      }
      return null
    } catch (error) {
      console.error('添加消息失败:', error)
      return null
    }
  }

  // 更新会话标题
  const editSession = async (id: string, title: string) => {
    if (!id || !title.trim()) return false
    
    try {
      const token = localStorage.getItem('token')
      if (!token) {
        return false
      }
      
      // 确保token格式正确
      const tokenToUse = token.startsWith('Bearer ') ? token : `Bearer ${token}`
      
      // 使用正确的API地址并传递title参数
      const apiUrl = `${API_BASE_URL}/api/sessions/${id}?title=${encodeURIComponent(title)}`
      console.log('编辑会话请求URL:', apiUrl)
      
      const response = await axios.put(
        apiUrl,
        null, // 空请求体，参数通过URL传递
        {
          headers: {
            Authorization: tokenToUse,
            'Content-Type': 'application/json'
          }
        }
      )
      
      console.log('编辑会话响应状态:', response.status)
      
      if (response.status === 200) {
        // 更新本地会话列表中的标题
        const sessionIndex = sessions.value.findIndex(s => s.id === id)
        if (sessionIndex !== -1) {
          sessions.value[sessionIndex].title = title
        }
        return true
      }
      return false
    } catch (error) {
      console.error('编辑会话标题失败:', error)
      return false
    }
  }

  // 删除会话
  const removeSession = async (id: string) => {
    if (!id) return false
    
    try {
      const token = localStorage.getItem('token')
      if (!token) {
        return false
      }
      
      // 确保token格式正确
      const tokenToUse = token.startsWith('Bearer ') ? token : `Bearer ${token}`
      
      console.log(`准备删除会话: ${id}`)
      
      const response = await axios.delete(`${API_BASE_URL}/api/sessions/${id}`, {
        headers: {
          Authorization: tokenToUse,
          'Content-Type': 'application/json'
        }
      })
      
      console.log('删除会话响应状态:', response.status)
      
      if (response.status === 200) {
        const index = sessions.value.findIndex(item => item.id === id)
        if (index !== -1) {
          sessions.value.splice(index, 1)
          
          // 如果删除的是当前会话，切换到第一个会话或清空
          if (currentSessionId.value === id) {
            if (sessions.value.length > 0) {
              await setCurrentSession(sessions.value[0].id)
            } else {
              currentSessionId.value = ''
              localStorage.removeItem('currentSessionId')
              currentMessages.value = []
            }
          }
        }
        return true
      }
      return false
    } catch (error) {
      console.error('删除会话失败:', error)
      return false
    }
  }

  // 清空所有会话
  const clearSessions = async () => {
    try {
      const token = localStorage.getItem('token')
      if (!token) {
        return false
      }
      
      // 确保token格式正确
      const tokenToUse = token.startsWith('Bearer ') ? token : `Bearer ${token}`
      
      console.log('准备清空所有会话')
      
      const response = await axios.delete(`${API_BASE_URL}/api/sessions`, {
        headers: {
          Authorization: tokenToUse,
          'Content-Type': 'application/json'
        }
      })
      
      console.log('清空会话响应状态:', response.status)
      
      if (response.status === 200) {
        sessions.value = []
        currentSessionId.value = ''
        localStorage.removeItem('currentSessionId')
        currentMessages.value = []
        return true
      }
      return false
    } catch (error) {
      console.error('清空会话失败:', error)
      return false
    }
  }

  return {
    sessions,
    currentSessionId,
    currentMessages,
    loading,
    messageLoading,
    initialize,
    loadSessions,
    createNewSession,
    setCurrentSession,
    editSession,
    removeSession,
    clearSessions,
    loadSessionMessages,
    addMessage
  }
})

export function useSessionStoreWithOut() {
  return useSessionStore(store)
} 