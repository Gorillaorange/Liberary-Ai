import { defineStore } from 'pinia'
import { store } from '@/store'
import { ref, computed } from 'vue'

export interface UserPreference {
  bookTypes: string[]
  authors: string[]
  topics: string[]
  lastSearches: string[]
  recentInterests: string[]
}

export interface BookRecommendation {
  id: string
  title: string
  author: string
  cover: string
  description: string
  type: string
  tags: string[]
  rating: number
}

export const useUserProfileStore = defineStore('user-profile', () => {
  // 用户偏好数据
  const userPreferences = ref<UserPreference>({
    bookTypes: [],
    authors: [],
    topics: [],
    lastSearches: [],
    recentInterests: []
  })
  
  // 图书推荐列表
  const bookRecommendations = ref<BookRecommendation[]>([])
  
  // 分析聊天内容获取用户偏好
  const analyzeChat = (content: string) => {
    // 图书类型关键词
    const bookTypeKeywords = {
      '小说': ['小说', '故事', '情节', '人物', '剧情'],
      '科技': ['科技', '技术', '编程', '计算机', 'IT', '人工智能', 'AI', '编码'],
      '历史': ['历史', '过去', '古代', '朝代', '年代', '文明'],
      '科学': ['科学', '物理', '化学', '生物', '自然', '宇宙', '数学'],
      '哲学': ['哲学', '思考', '思想', '意义', '价值', '存在'],
      '艺术': ['艺术', '绘画', '音乐', '电影', '设计', '美学'],
      '商业': ['商业', '经济', '金融', '管理', '创业', '投资'],
      '心理学': ['心理', '心理学', '情绪', '行为', '认知', '潜意识'],
      '教育': ['教育', '学习', '教学', '课程', '教材', '学校'],
      '生活': ['生活', '健康', '饮食', '运动', '休闲', '旅游']
    }
    
    // 分析内容中的图书类型
    Object.entries(bookTypeKeywords).forEach(([type, keywords]) => {
      const matchCount = keywords.reduce((count, keyword) => {
        const regex = new RegExp(keyword, 'gi')
        const matches = content.match(regex)
        return count + (matches ? matches.length : 0)
      }, 0)
      
      if (matchCount > 0 && !userPreferences.value.bookTypes.includes(type)) {
        userPreferences.value.bookTypes.push(type)
      }
    })
    
    // 分析可能的兴趣主题
    const topicRegex = /喜欢|感兴趣|关注|想了解|学习/gi
    const topicMatches = content.match(topicRegex)
    
    if (topicMatches) {
      // 提取主题关键词
      const sentence = content.split(/[.,。，！？!?]/g)
      sentence.forEach(s => {
        topicMatches.forEach(match => {
          if (s.includes(match)) {
            // 提取兴趣主题
            const topic = s.slice(s.indexOf(match) + match.length).trim()
            if (topic && topic.length > 1 && topic.length < 10) {
              if (!userPreferences.value.recentInterests.includes(topic)) {
                userPreferences.value.recentInterests.push(topic)
              }
            }
          }
        })
      })
    }
    
    // 保存用户偏好到本地存储
    savePreferencesToLocalStorage()
  }
  
  // 加载用户配置文件
  const loadUserProfile = () => {
    try {
      const savedPreferences = localStorage.getItem('userPreferences')
      if (savedPreferences) {
        userPreferences.value = JSON.parse(savedPreferences)
      }
      
      // 加载推荐图书数据
      loadRecommendedBooks()
    } catch (error) {
      console.error('加载用户配置文件失败:', error)
    }
  }
  
  // 保存偏好到本地存储
  const savePreferencesToLocalStorage = () => {
    try {
      localStorage.setItem('userPreferences', JSON.stringify(userPreferences.value))
    } catch (error) {
      console.error('保存用户偏好失败:', error)
    }
  }
  
  // 加载推荐图书数据 (模拟数据)
  const loadRecommendedBooks = () => {
    // 这里使用模拟数据
    const mockBooks: BookRecommendation[] = [
      {
        id: '1',
        title: '沙丘',
        author: '弗兰克·赫伯特',
        cover: 'https://images.unsplash.com/photo-1589998059171-988d887df646?ixlib=rb-4.0.3',
        description: '在遥远的未来，人类帝国统治着无数星球，贵族家族之间明争暗斗，争夺宇宙中最珍贵的资源——香料。',
        type: '科幻',
        tags: ['科幻', '哲学', '政治'],
        rating: 4.8
      },
      {
        id: '2',
        title: '三体',
        author: '刘慈欣',
        cover: 'https://images.unsplash.com/photo-1614322694587-9f9bab6b9d80?ixlib=rb-4.0.3',
        description: '在文化大革命期间的中国，一次偶然的军事雷达实验向宇宙深处发送了一段信息，引发了地球文明与三体文明的首次接触。',
        type: '科幻',
        tags: ['硬科幻', '社会', '宇宙'],
        rating: 4.9
      },
      {
        id: '3',
        title: '百年孤独',
        author: '加西亚·马尔克斯',
        cover: 'https://images.unsplash.com/photo-1580537659466-0a9bfa916a54?ixlib=rb-4.0.3',
        description: '这是一部关于布恩迪亚家族七代人的传奇故事，通过这个家族的兴衰绘制了人类文明的缩影。',
        type: '文学',
        tags: ['魔幻现实主义', '家族', '历史'],
        rating: 4.7
      },
      {
        id: '4',
        title: '深入理解计算机系统',
        author: 'Randal E. Bryant',
        cover: 'https://images.unsplash.com/photo-1629654291663-b91ad427698f?ixlib=rb-4.0.3',
        description: '这本书深入剖析计算机系统的基本原理，从程序员的视角解释计算机如何工作。',
        type: '科技',
        tags: ['计算机', '编程', '系统设计'],
        rating: 4.9
      },
      {
        id: '5',
        title: '明朝那些事儿',
        author: '当年明月',
        cover: 'https://images.unsplash.com/photo-1614332287897-cdc485fa562d?ixlib=rb-4.0.3',
        description: '以通俗易懂的语言，讲述了明朝三百年的历史风云，带你了解那段波澜壮阔的历史。',
        type: '历史',
        tags: ['中国历史', '明朝', '通俗历史'],
        rating: 4.6
      },
      {
        id: '6',
        title: '人类简史',
        author: '尤瓦尔·赫拉利',
        cover: 'https://images.unsplash.com/photo-1592431913823-7af6b323da2b?ixlib=rb-4.0.3',
        description: '这本书讲述了人类如何从一个普通的物种进化成为地球的主宰，塑造了整个星球的生态系统。',
        type: '科学',
        tags: ['历史', '人类学', '科普'],
        rating: 4.7
      }
    ]
    
    bookRecommendations.value = mockBooks
  }
  
  // 获取基于用户偏好的推荐
  const getRecommendations = computed(() => {
    if (userPreferences.value.bookTypes.length === 0) {
      return bookRecommendations.value
    }
    
    // 根据用户偏好过滤推荐图书
    return bookRecommendations.value.filter(book => {
      return userPreferences.value.bookTypes.some(type => 
        book.type.includes(type) || book.tags.some(tag => tag.includes(type))
      )
    })
  })
  
  // 添加搜索记录
  const addSearchHistory = (query: string) => {
    if (!userPreferences.value.lastSearches.includes(query)) {
      userPreferences.value.lastSearches.unshift(query)
      
      // 限制最多保存10条搜索记录
      if (userPreferences.value.lastSearches.length > 10) {
        userPreferences.value.lastSearches.pop()
      }
      
      savePreferencesToLocalStorage()
    }
  }
  
  // 清除用户画像数据
  const clearUserProfile = () => {
    userPreferences.value = {
      bookTypes: [],
      authors: [],
      topics: [],
      lastSearches: [],
      recentInterests: []
    }
    
    savePreferencesToLocalStorage()
  }
  
  return {
    userPreferences,
    bookRecommendations,
    analyzeChat,
    loadUserProfile,
    getRecommendations,
    addSearchHistory,
    clearUserProfile
  }
})

export function useUserProfileStoreWithOut() {
  return useUserProfileStore(store)
} 