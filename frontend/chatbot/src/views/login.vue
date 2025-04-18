<script lang="ts" setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import type { FormInst, FormRules } from 'naive-ui'
import { login } from '@/api/auth'
import { CryptoUtil } from '@/utils/crypto'
import ParticlesBackground from '@/components/ParticlesBackground.vue'

const router = useRouter()
const message = useMessage()

// 表单引用，用于表单验证
const formRef = ref<FormInst | null>(null)

// 表单数据
const formData = reactive({
  username: '',
  password: '',
  rememberMe: false
})

// 表单验证规则
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, message: '用户名长度不能小于3个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能小于6个字符', trigger: 'blur' }
  ]
}

// 登录提交状态
const loading = ref(false)

// 处理登录请求
const handleLogin = () => {
  if (!formData.username || !formData.password) {
    message.error('请输入用户名和密码')
    return
  }
  
  loading.value = true
  
  try {
    // 对密码进行MD5加密
    const encryptedPassword = CryptoUtil.encryptPassword(formData.password)
    console.log('加密后的密码:', encryptedPassword)
    
    login({
      username: formData.username,
      password: encryptedPassword,
      rememberMe: formData.rememberMe
    }).then((response: any) => {
      loading.value = false
      
      console.log('登录响应:', response)
      // 检查response的格式
      if (response.data && response.data.error === 0) {
        // 将token保存在localStorage中
        const token = response.data.data.token
        localStorage.setItem('token', token)
        localStorage.setItem('username', response.data.data.username)
        
        // 解析并输出token信息（仅用于调试）
        console.log('保存的token:', token)
        try {
          const tokenParts = token.split('.')
          if (tokenParts.length === 3) {
            const payload = JSON.parse(atob(tokenParts[1]))
            console.log('token解析结果:', payload)
            console.log('token中的userId:', payload.userId)
            
            // 不再需要存储userId到localStorage
          }
        } catch (e) {
          console.error('解析token失败:', e)
        }
        
        message.success('登录成功')
        router.push('/chat')
      } else {
        // 登录失败，显示错误信息
        message.error(response.data?.msg || '登录失败')
      }
    }).catch(error => {
      loading.value = false
      console.error('登录错误:', error)
      message.error(error.message || '登录失败，请稍后再试')
    })
  } catch (error) {
    loading.value = false
    console.error('密码加密错误:', error)
    message.error('登录失败：密码加密错误')
  }
}

// 初始化时检查是否有记住的用户名
const initRememberedUsername = () => {
  const rememberedUsername = localStorage.getItem('username')
  if (rememberedUsername) {
    formData.username = rememberedUsername
    formData.rememberMe = true
  }
}

// 跳转到注册页面
const goToRegister = () => {
  router.push('/register')
}

// 页面加载时执行
initRememberedUsername()
</script>

<template>
  <div class="login-container">
    <ParticlesBackground />
    
    <div class="login-card">
      <div class="login-header">
        <img src="/11.jpg" alt="Logo" class="logo-image" />
        <h1 class="login-title">图书馆智能助手</h1>
      </div>
      
      <n-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-placement="left"
        require-mark-placement="right-hanging"
        label-width="auto"
        class="login-form"
      >
        <n-form-item path="username" label="用户名">
          <n-input
            v-model:value="formData.username"
            placeholder="请输入用户名"
            @keyup.enter="handleLogin"
          />
        </n-form-item>
        
        <n-form-item path="password" label="密码">
          <n-input
            v-model:value="formData.password"
            type="password"
            placeholder="请输入密码"
            show-password-on="click"
            @keyup.enter="handleLogin"
          />
        </n-form-item>
        
        <div class="form-options">
          <n-checkbox v-model:checked="formData.rememberMe">
            记住我
          </n-checkbox>
          <a href="#" class="forgot-password">忘记密码?</a>
        </div>
        
        <div class="form-actions">
          <n-button
            type="primary"
            block
            :loading="loading"
            @click="handleLogin"
          >
            登录
          </n-button>
        </div>
        
        <div class="register-link">
          没有账号？<a href="javascript:;" @click="goToRegister">去注册</a>
        </div>
      </n-form>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  position: relative;
  z-index: 1;
}

.login-card {
  width: 400px;
  padding: 30px;
  background: var(--card-bg-dark);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
  position: relative;
  z-index: 2;
  border: 1px solid var(--card-border-dark);
  backdrop-filter: blur(5px);
}

.login-header {
  text-align: center;
  margin-bottom: 40px;
}

.logo-image {
  width: 80px;
  height: 80px;
  object-fit: contain;
  margin-bottom: 16px;
}

.login-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-light);
  margin: 0;
}

.login-form {
  margin-top: 20px;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.forgot-password {
  font-size: 14px;
  color: var(--accent-color);
  text-decoration: none;
}

.forgot-password:hover {
  color: var(--accent-hover);
}

.form-actions {
  margin-top: 16px;
}

.register-link {
  text-align: center;
  margin-top: 16px;
  color: var(--text-light-secondary);
}

.register-link a {
  color: var(--accent-color);
  text-decoration: none;
}

.register-link a:hover {
  color: var(--accent-hover);
}

:deep(.n-input) {
  background-color: var(--secondary-dark);
}

:deep(.n-input__input) {
  color: var(--text-light);
}

:deep(.n-form-item-label) {
  color: var(--text-light);
}
</style> 