<script lang="tsx" setup>
import { systemTitle } from '@/base'
import { logout } from '@/api/auth'

interface Props {
  transparent?: boolean
  hasBorder?: boolean
}
withDefaults(
  defineProps<Props>(),
  {
    transparent: true,
    hasBorder: true
  }
)

const router = useRouter()
const message = useMessage()

// 处理退出登录
const handleLogout = async () => {
  try {
    await logout()
    // 清除本地存储的token和用户名
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('currentSessionId')
    
    // 直接跳转到登录页面
    router.push('/login')
    
    // 跳转后显示消息
    message.success('退出登录成功')
  } catch (error) {
    console.error('退出登录失败:', error)
    // 即使出错也清除token并重定向
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('currentSessionId')
    router.push('/login')
    message.error('退出登录失败，但已清除登录状态')
  }
}

// 检查用户是否登录
const isLoggedIn = computed(() => {
  return !!localStorage.getItem('token')
})

// 获取用户名
const username = computed(() => {
  return localStorage.getItem('username') || '用户'
})
</script>

<template>
  <header
    class="navigation-nav-header-container"
    :class="[
      transparent
        ? 'bg-transparent'
        : 'bg-transparent',
      hasBorder
        ? 'border-bottom'
        : ''
    ]"
  >
    <div
      class="header-left"
    >
    </div>
    <div class="flex-1">
      <div
        flex="~ col items-center justify-center"
        px-36px
      >
        <div
          flex="~ items-center justify-center"
          class="text-20 system-title"
          select-none
          cursor-pointer
        >
          <div class="size-24 i-streamline-emojis:open-book"></div>
          <div class="flex-1 pl-10 font-600 text-center">{{ systemTitle }}</div>
        </div>
        <slot name="bottom"></slot>
      </div>
    </div>

    <div class="header-right">
      <template v-if="isLoggedIn">
        <div class="user-info">
          <span class="username">{{ username }}</span>
        </div>
        <n-button 
          class="logout-btn" 
          @click="handleLogout"
        >
          退出登录
        </n-button>
      </template>
    </div>
  </header>
</template>

<style lang="scss" scoped>
.navigation-nav-header-container {
  --at-apply: w-full flex items-center justify-center py-10;
  background-color: rgba(13, 25, 42, 0.7);
  backdrop-filter: blur(5px);
  transition: all 0.3s ease;
  
  &.border-bottom {
    border-bottom: 1px solid var(--card-border-dark);
  }

  .header-left,
  .header-right {
    --at-apply: flex items-center h-full text-16;
  }

  .header-left {
    --at-apply: h-50px;
  }
  
  .system-title {
    color: var(--text-light);
  }

  .header-right {
    --at-apply: flex items-center h-full text-16 gap-10px mr-15px;
    
    .user-info {
      --at-apply: flex items-center;
      
      .username {
        --at-apply: font-500 mr-10px;
        color: var(--text-light);
      }
    }
    
    .logout-btn {
      --at-apply: px-15px;
      background-color: rgba(220, 53, 69, 0.8);
      color: white;
      border: none;
      
      &:hover {
        background-color: rgba(220, 53, 69, 1);
      }
    }
  }
}
</style>
