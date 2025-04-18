<script lang="ts" setup>
import SideBar from '@/components/SideBar/index.vue'
import ParticlesBackground from '@/components/ParticlesBackground.vue'
import { useUserProfileStore } from '@/store/hooks/useUserProfileStore'
import { useSessionStore } from '@/store/hooks/useSessionStore'
import { onMounted, ref, computed, onUnmounted } from 'vue'
import { useMessage } from 'naive-ui'
import { getFormattedToken } from '@/utils/token'

// 图书DTO接口定义
interface BookDTO {
  id?: number;
  bookId?: string | number;
  title?: string;
  description?: string;
  author?: string;
  rating?: number;
  similarity?: number;
  tags?: string[];
  category?: string;
  coverUrl?: string;
}

const userProfileStore = useUserProfileStore()
const sessionStore = useSessionStore()
const message = useMessage()

// 用户兴趣标签
const userInterestTags = ref<string[]>([])
const isLoadingTags = ref(false)

// 是否已显示过推荐提示
const hasShownRecommendationTip = ref(false)

// 加载用户兴趣标签
const loadUserInterestTags = async () => {
  try {
    isLoadingTags.value = true
    const token = getFormattedToken()
    if (!token) {
      message.error('未登录或会话已过期，请重新登录')
      return
    }
    
    console.log('开始加载用户兴趣标签...')
    const response = await fetch('http://localhost:8080/api/user/interest-tags', {
      method: 'GET',
      headers: {
        'Authorization': token,
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      },
      credentials: 'include',
      mode: 'cors'
    })
    
    if (response.ok) {
      const data = await response.json()
      console.log('获取到的兴趣标签数据:', JSON.stringify(data))
      
      if (data.success && Array.isArray(data.data)) {
        // 过滤掉空值和非字符串值
        userInterestTags.value = data.data
          .filter(tag => tag && typeof tag === 'string' && tag.trim().length > 0)
          .map(tag => tag.trim())
        
        console.log('处理后的兴趣标签数组:', userInterestTags.value)
        console.log('兴趣标签数量:', userInterestTags.value.length)
        
        if (userInterestTags.value.length === 0 && data.data.length > 0) {
          console.warn('返回的标签数据存在但格式可能不正确:', data.data)
          message.warning('返回的标签数据格式不正确')
        }
      } else {
        console.warn('返回数据格式不正确:', data)
        message.warning('获取兴趣标签数据格式不正确')
      }
    } else {
      console.error('获取用户兴趣标签失败:', response.status, response.statusText)
      const errorData = await response.text()
      console.error('错误详情:', errorData)
      message.error('获取兴趣标签失败: ' + response.statusText)
    }
  } catch (error) {
    console.error('加载用户兴趣标签出错:', error)
    message.error('加载兴趣标签时发生错误')
  } finally {
    isLoadingTags.value = false
  }
}

// 加载用户配置
onMounted(() => {
  userProfileStore.loadUserProfile()
  loadUserInterestTags()
  // 不再自动加载推荐，而是让用户通过按钮明确触发
})

// 用户偏好
const userPreferences = computed(() => userProfileStore.userPreferences)

// 推荐图书
const recommendedBooks = ref<BookDTO[]>([])
const isLoadingRecommendations = ref(false)

// API基础URL
const API_BASE_URL = 'http://localhost:8080'

// 加载书籍推荐
const loadBookRecommendations = async () => {
  isLoadingRecommendations.value = true;
  try {
    const token = getFormattedToken()
    if (!token) {
      message.error('未登录或会话已过期，请重新登录')
      return
    }

    const response = await fetch(`${API_BASE_URL}/api/books/recommend-books?shouldGenerateNewProfile=false`, {
      method: 'GET',
      headers: {
        'Authorization': token,
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      },
      credentials: 'include',
      mode: 'cors'
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const data = await response.json();
    console.log("加载推荐数据:", data);
    
    if (data.recommendations && Array.isArray(data.recommendations)) {
      recommendedBooks.value = data.recommendations;
    } else {
      console.error("接收到了非预期的数据格式:", data);
      message.error('推荐数据格式不正确');
      recommendedBooks.value = [];
    }
  } catch (error) {
    console.error("加载推荐失败:", error);
    message.error('加载推荐失败: ' + (error instanceof Error ? error.message : '未知错误'));
    recommendedBooks.value = [];
  } finally {
    isLoadingRecommendations.value = false;
  }
};

// 生成新的图书推荐
const generateNewRecommendations = async () => {
  isLoadingRecommendations.value = true;
  try {
    const token = getFormattedToken()
    if (!token) {
      message.error('未登录或会话已过期，请重新登录')
      return
    }

    const response = await fetch(`${API_BASE_URL}/api/books/recommend-books?shouldGenerateNewProfile=true`, {
      method: 'GET',
      headers: {
        'Authorization': token,
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      },
      credentials: 'include',
      mode: 'cors'
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const data = await response.json();
    console.log("生成新推荐数据:", data);
    
    if (data.recommendations && Array.isArray(data.recommendations)) {
      recommendedBooks.value = data.recommendations;
    } else {
      console.error("接收到了非预期的数据格式:", data);
      message.error('推荐数据格式不正确');
      recommendedBooks.value = [];
    }
  } catch (error) {
    console.error("生成推荐失败:", error);
    message.error('生成推荐失败: ' + (error instanceof Error ? error.message : '未知错误'));
    recommendedBooks.value = [];
  } finally {
    isLoadingRecommendations.value = false;
  }
};

// 生成用户画像后加载推荐
const generateUserProfileAndLoadRecommendations = async () => {
  await generateUserProfile()
  // 成功生成用户画像后生成新的图书推荐
  if (userInterestTags.value.length > 0) {
    generateNewRecommendations()
  }
}

// 显示的标签数量限制
const maxTagsToShow = 5

// 用户分析数据
const userAnalysisData = computed(() => {
  const data: Array<{ title: string; items: string[] }> = []
  
  // 添加图书类型偏好
  if (userPreferences.value.bookTypes.length > 0) {
    data.push({
      title: '您喜欢的图书类型',
      items: userPreferences.value.bookTypes.slice(0, maxTagsToShow)
    })
  }
  
  // 添加兴趣主题
  if (userPreferences.value.recentInterests.length > 0) {
    data.push({
      title: '您感兴趣的主题',
      items: userPreferences.value.recentInterests.slice(0, maxTagsToShow)
    })
  }
  
  // 添加搜索历史
  if (userPreferences.value.lastSearches.length > 0) {
    data.push({
      title: '最近搜索',
      items: userPreferences.value.lastSearches.slice(0, maxTagsToShow)
    })
  }
  
  return data
})

// 是否有足够的用户画像数据
const hasUserProfile = computed(() => {
  return userPreferences.value.bookTypes.length > 0 || 
         userPreferences.value.recentInterests.length > 0 ||
         userInterestTags.value.length > 0
})

// 清除用户画像数据
const clearUserProfile = async () => {
  if (confirm('确定要清除您的阅读偏好数据吗？')) {
    await userProfileStore.clearUserProfile()
    userInterestTags.value = []
  }
}

// 生成用户画像
const generateUserProfile = async () => {
  try {
    // 获取token
    const token = getFormattedToken()
    // 确保token不为null
    if (!token) {
      message.error('未登录或会话已过期，请重新登录');
      return;
    }
    
    // 调用后端API生成用户画像
    const response = await fetch('/api/user/generate-profile', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': token
      }
    });
    
    if (response.ok) {
      const data = await response.json();
      
      // 检查是否有提示消息
      if (data.message && data.interests && data.interests.length === 0) {
        // 显示提示信息
        message.info(data.message);
      } else {
        // 重新加载用户配置和兴趣标签
        await userProfileStore.loadUserProfile();
        await loadUserInterestTags();
        // 显示成功消息
        message.success('用户画像生成成功');
      }
    } else {
      message.error('生成用户画像失败，请稍后再试');
    }
  } catch (error) {
    console.error('生成用户画像错误:', error);
    message.error('生成用户画像时出现错误');
  }
}

// 是否显示空推荐提示
const shouldShowEmptyRecommendation = computed(() => {
  return recommendedBooks.value.length === 0 && !isLoadingRecommendations.value;
})

// 控制按钮显示/隐藏的逻辑
const isScrollingDown = ref(false)
let lastScrollTop = 0

const handleScroll = () => {
  const st = window.pageYOffset || document.documentElement.scrollTop
  isScrollingDown.value = st > lastScrollTop && st > 100
  lastScrollTop = st <= 0 ? 0 : st
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})
</script>

<template>
  <div class="aihub-container">
    <ParticlesBackground />
    <SideBar />
    <div class="aihub-content">
      <div class="aihub-header">
        <h1>图书智能推荐</h1>
        <div class="header-buttons">
          <n-button size="small" type="primary" @click="generateUserProfile">生成画像</n-button>
        </div>
      </div>
      
      <div class="aihub-wrapper">
        <!-- 用户画像部分 -->
        <div class="profile-section">
          <div class="profile-header">
            <h2>我的阅读画像</h2>
            <div v-if="hasUserProfile">
              <n-button size="small" @click="clearUserProfile">清空画像</n-button>
            </div>
          </div>
          
          <div v-if="hasUserProfile" class="profile-content">
            <div v-for="(group, index) in userAnalysisData" :key="index" class="profile-group">
              <h3>{{ group.title }}</h3>
              <div class="tag-container">
                <n-tag v-for="(item, idx) in group.items" :key="idx" type="info" size="medium">
                  {{ item }}
                </n-tag>
              </div>
            </div>
            
            <!-- 添加AI分析的兴趣标签 -->
            <div v-if="userInterestTags.length > 0" class="profile-group">
              <h3>AI分析的兴趣标签</h3>
              <div class="tag-container">
                <n-tag v-for="(tag, idx) in userInterestTags.slice(0, 10)" :key="idx" type="success" size="medium">
                  {{ tag }}
                </n-tag>
              </div>
            </div>
            
            <div class="profile-tip">
              <p>这些标签是基于您的聊天内容和阅读历史分析得出的，会随着您的使用不断更新。</p>
            </div>
          </div>
          
          <div v-else class="empty-profile">
            <div v-if="isLoadingTags" class="loading-container">
              <n-spin size="medium" />
              <p>正在加载您的阅读画像...</p>
            </div>
            <template v-else>
              <div class="empty-icon">
                <i class="iconfont ai-user"></i>
              </div>
              <p>您的阅读画像尚未生成</p>
              <span>与智能助手多聊聊您的阅读喜好，系统会自动分析并生成您的阅读画像。</span>
              <n-button class="mt-4" type="primary" size="small" @click="generateUserProfile">
                立即生成画像
              </n-button>
            </template>
          </div>
        </div>
        
        <!-- 推荐图书部分 -->
        <div class="recommendations-section">
          <n-card title="基于您的兴趣推荐的图书">
            <n-spin :show="isLoadingRecommendations">
              <n-empty v-if="recommendedBooks.length === 0" description="还没有图书推荐">
                <template #extra>
                  <div class="empty-content">
                    <p class="empty-tip">点击右下角的按钮开始获取推荐</p>
                  </div>
                </template>
              </n-empty>
              <div v-else class="books-container">
                <n-card 
                  v-for="(item, index) in recommendedBooks" 
                  :key="index" 
                  class="book-card" 
                  :title="item.title || '未知标题'" 
                  hoverable
                >
                  <!-- <template #cover>
                    <img
                      alt="book cover"
                      :src="item.coverUrl || 'https://gw.alipayobjects.com/zos/rmsportal/JiqGstEfoWAOHiTxclqi.png'"
                      style="height: 150px; object-fit: cover;"
                    />
                  </template> -->
                  <p class="book-description">{{ item.description ? (item.description.substring(0, 100) + '...') : '暂无描述' }}</p>
                  <div class="book-tags">
                    <n-tag type="info" v-if="item.similarity !== undefined">
                      相似度: {{ (Number(item.similarity) * 100).toFixed(2) }}%
                    </n-tag>
                    <n-tag type="success" v-if="item.category">
                      {{ item.category }}
                    </n-tag>
                    <n-tag type="warning" v-if="item.bookId || item.id">
                      ID: {{ item.bookId || item.id || '未知' }}
                    </n-tag>
                  </div>
                  <div class="book-author" v-if="item.author">
                    <p>作者: {{ item.author }}</p>
                  </div>
                </n-card>
              </div>
            </n-spin>
          </n-card>
        </div>
      </div>
    </div>
    
    <!-- 悬浮按钮 -->
    <transition name="fade-slide">
      <div class="action-buttons" v-show="!isScrollingDown">
        <n-button-group>
          <n-tooltip placement="left">
            <template #trigger>
              <n-button circle type="primary" @click="loadBookRecommendations" :loading="isLoadingRecommendations">
                📚
              </n-button>
            </template>
            查看推荐
          </n-tooltip>
          <n-tooltip placement="left">
            <template #trigger>
              <n-button circle type="info" @click="generateNewRecommendations" :loading="isLoadingRecommendations">
                🔄
              </n-button>
            </template>
            AI重新推荐
          </n-tooltip>
        </n-button-group>
      </div>
    </transition>
  </div>
</template>

<style lang="scss" scoped>
.aihub-container {
  display: flex;
  height: 100vh;
  overflow: hidden;
  position: relative;
}

.aihub-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background-color: rgba(26, 35, 51, 0.1);
  backdrop-filter: blur(0.5px);
}

.aihub-header {
  margin-bottom: 30px;
  padding: 20px;
  background: rgba(38, 50, 71, 0.4);
  backdrop-filter: blur(10px);
  border-radius: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  
  h1 {
    color: var(--text-light);
    font-size: 24px;
    margin: 0;
    font-weight: 600;
  }

  .header-buttons {
    display: flex;
    gap: 8px;
  }
}

.aihub-wrapper {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 20px;
  
  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
}

.profile-section {
  background: rgba(38, 50, 71, 0.4);
  backdrop-filter: blur(10px);
  border-radius: 10px;
  padding: 20px;
  height: fit-content;
  
  .profile-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    
    h2 {
      color: var(--text-light);
      font-size: 18px;
      margin: 0;
    }
  }
  
  .profile-content {
    .profile-group {
      margin-bottom: 15px;
      
      h3 {
        color: var(--text-light);
        font-size: 16px;
        margin-bottom: 10px;
      }
      
      .tag-container {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
      }
    }
    
    .profile-tip {
      margin-top: 20px;
      padding-top: 15px;
      border-top: 1px solid rgba(255, 255, 255, 0.1);
      
      p {
        color: var(--text-light-secondary);
        font-size: 13px;
        line-height: 1.6;
      }
    }
  }
  
  .empty-profile {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 30px 15px;
    text-align: center;
    
    .loading-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 15px;
      
      p {
        color: var(--text-light);
        font-size: 16px;
        margin: 0;
      }
    }
    
    .empty-icon {
      width: 60px;
      height: 60px;
      background: rgba(74, 111, 164, 0.2);
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 15px;
      
      i {
        font-size: 30px;
        color: var(--accent-color);
      }
    }
    
    p {
      color: var(--text-light);
      font-size: 16px;
      margin-bottom: 10px;
      font-weight: 500;
    }
    
    span {
      color: var(--text-light-secondary);
      font-size: 14px;
      line-height: 1.6;
      margin-bottom: 15px;
    }
  }
}

.books-section {
  background: rgba(38, 50, 71, 0.4);
  backdrop-filter: blur(10px);
  border-radius: 10px;
  padding: 20px;
  
  h2 {
    color: var(--text-light);
    font-size: 18px;
    margin-bottom: 20px;
  }
  
  .books-container {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 20px;
  }
  
  .book-card {
    background: rgba(26, 35, 51, 0.6);
    border-radius: 10px;
    overflow: hidden;
    transition: transform 0.3s ease, box-shadow 0.3s ease;
    
    &:hover {
      transform: translateY(-5px);
      box-shadow: 0 10px 20px rgba(0, 0, 0, 0.2);
    }
    
    .book-cover {
      position: relative;
      height: 180px;
      
      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
      
      .book-rating {
        position: absolute;
        top: 10px;
        right: 10px;
        background: rgba(0, 0, 0, 0.6);
        color: #fff;
        padding: 5px 8px;
        border-radius: 15px;
        font-size: 12px;
        display: flex;
        align-items: center;
        
        i {
          color: gold;
          margin-right: 5px;
        }
      }
    }
    
    .book-info {
      padding: 15px;
      
      .book-title {
        color: var(--text-light);
        font-size: 16px;
        margin: 0 0 5px;
        font-weight: 600;
      }
      
      .book-author {
        color: var(--text-light-secondary);
        font-size: 14px;
        margin: 0 0 10px;
      }
      
      .book-tags {
        display: flex;
        flex-wrap: wrap;
        gap: 5px;
        margin-bottom: 10px;
      }
      
      .book-desc {
        color: var(--text-light);
        font-size: 13px;
        line-height: 1.6;
        display: -webkit-box;
        -webkit-line-clamp: 3;
        -webkit-box-orient: vertical;
        overflow: hidden;
        margin: 0;
      }
    }
  }
}

// 响应式调整
@media (max-width: 768px) {
  .aihub-wrapper {
    grid-template-columns: 1fr;
  }
  
  .books-section .books-container {
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  }
}

.mt-4 {
  margin-top: 16px;
}

.button-group {
  display: flex;
  gap: 8px;
  color: black;
}

.empty-recommendation {
  text-align: center;
  padding: 10px 0;
  
  .empty-title {
    font-size: 16px;
    font-weight: 500;
    color: var(--text-light);
    margin-bottom: 12px;
  }
  
  .empty-description {
    color: var(--text-light-secondary);
    font-size: 14px;
    
    p {
      margin-bottom: 8px;
    }
    
    ul {
      text-align: left;
      padding-left: 20px;
      margin-top: 5px;
      
      li {
        margin-bottom: 5px;
        line-height: 1.5;
      }
    }
  }
}

.recommendation-card {
  background: rgba(38, 50, 71, 0.4);
  backdrop-filter: blur(10px);
  border-radius: 10px;
  
  :deep(.n-card-header) {
    padding-bottom: 10px;
  }
  
  :deep(.n-card__title) {
    font-size: 18px;
    color: var(--text-light);
  }
}

.books-container {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
  margin-top: 16px;
}

.book-card {
  background: rgba(26, 35, 51, 0.6);
  border-radius: 10px;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  overflow: hidden;
  
  &:hover {
    transform: translateY(-5px);
    box-shadow: 0 10px 20px rgba(0, 0, 0, 0.2);
  }
  
  :deep(.n-card__title) {
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 5px;
  }
  
  .book-description {
    color: var(--text-light);
    font-size: 13px;
    line-height: 1.6;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
    margin-bottom: 10px;
  }
  
  .book-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 10px;
  }
}

.empty-content {
  text-align: center;
  padding: 20px;
  
  .empty-tip {
    color: var(--text-light-secondary);
    font-size: 14px;
    margin: 0;
  }
}

// 悬浮按钮样式
.action-buttons {
  position: fixed;
  right: 30px;
  bottom: 30px;
  z-index: 100;
  display: flex;
  flex-direction: column;
  gap: 10px;
  
  .n-button-group {
    box-shadow: 0 4px 15px rgba(0, 0, 0, 0.2);
    border-radius: 50%;
    overflow: hidden;
    
    .n-button {
      font-size: 18px;  // 调整emoji大小
      line-height: 1;
      padding: 8px;    // 调整按钮内边距
      
      &:hover {
        transform: translateY(-2px);
        box-shadow: 0 6px 16px rgba(0, 0, 0, 0.15);
      }
      
      &:active {
        transform: translateY(0);
      }
    }
  }
}

// 按钮显示/隐藏动画
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(20px);
}

// 添加按钮悬停效果
.n-button {
  transition: all 0.3s ease;
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.15);
  }
  
  &:active {
    transform: translateY(0);
  }
}

// 添加图书卡片动画
.book-card {
  transition: all 0.3s ease;
  
  &:hover {
    transform: translateY(-5px);
    box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
  }
}
</style> 