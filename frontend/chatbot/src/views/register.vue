<script lang="ts" setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import type { FormInst, FormRules } from 'naive-ui'
import { register } from '@/api/auth'
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
  confirmPassword: '',
  grade: '',
  major: ''
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
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (rule, value) => {
        return value === formData.password
      },
      message: '两次输入的密码不一致',
      trigger: 'blur'
    }
  ],
  grade: [
    { required: true, message: '请输入年级', trigger: 'blur' }
  ],
  major: [
    { required: true, message: '请输入专业', trigger: 'blur' }
  ]
}

// 注册提交状态
const loading = ref(false)

// 处理注册请求
const handleRegister = () => {
  formRef.value?.validate(async (errors) => {
    if (errors) {
      message.error('请正确填写所有必填项')
      return
    }
    
    loading.value = true
    
    try {
      // 加密密码
      const encryptedPassword = await CryptoUtil.encryptPassword(formData.password)
      const encryptedConfirmPassword = await CryptoUtil.encryptPassword(formData.confirmPassword)
      
      const response = await register({
        username: formData.username,
        password: encryptedPassword,
        confirmPassword: encryptedConfirmPassword,
        grade: formData.grade,
        major: formData.major
      })
      
      if (response.error === 0) {
        // 显示成功消息
        message.success('注册成功，请登录')
        
        // 延迟后跳转
        setTimeout(() => {
          router.push('/login')
        }, 500)
      } else {
        message.error(response.msg || '注册失败，请稍后再试')
      }
    } catch (error) {
      message.error('注册请求失败，请稍后再试')
      console.error('注册错误:', error)
    } finally {
      loading.value = false
    }
  })
}

// 返回登录页
const goToLogin = () => {
  router.push('/login')
}
</script>

<template>
  <div class="register-container">
    <ParticlesBackground />
    
    <div class="register-card">
      <div class="register-header">
        <img src="/11.jpg" alt="Logo" class="logo-image" />
        <h1 class="register-title">图书馆智能助手</h1>
      </div>
      
      <n-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-placement="left"
        require-mark-placement="right-hanging"
        label-width="auto"
        class="register-form"
      >
        <n-form-item path="username" label="用户名">
          <n-input
            v-model:value="formData.username"
            placeholder="请输入用户名（至少3个字符）"
          />
        </n-form-item>
        
        <n-form-item path="password" label="密码">
          <n-input
            v-model:value="formData.password"
            type="password"
            placeholder="请输入密码（至少6个字符）"
            show-password-on="click"
          />
        </n-form-item>
        
        <n-form-item path="confirmPassword" label="确认密码">
          <n-input
            v-model:value="formData.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            show-password-on="click"
          />
        </n-form-item>
        
        <n-form-item path="grade" label="年级">
          <n-input
            v-model:value="formData.grade"
            placeholder="请输入年级"
          />
        </n-form-item>
        
        <n-form-item path="major" label="专业">
          <n-input
            v-model:value="formData.major"
            placeholder="请输入专业"
          />
        </n-form-item>
        
        <div class="form-actions">
          <n-button
            type="primary"
            block
            :loading="loading"
            @click="handleRegister"
          >
            注册
          </n-button>
        </div>
        
        <div class="login-link">
          已有账号？<a href="javascript:;" @click="goToLogin">去登录</a>
        </div>
      </n-form>
    </div>
  </div>
</template>

<style scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  position: relative;
  z-index: 1;
}

.register-card {
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

.register-header {
  text-align: center;
  margin-bottom: 40px;
}

.logo-image {
  width: 80px;
  height: 80px;
  object-fit: contain;
  margin-bottom: 16px;
}

.register-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-light);
  margin: 0;
}

.register-form {
  margin-top: 20px;
}

.form-actions {
  margin-top: 24px;
  margin-bottom: 16px;
}

.login-link {
  text-align: center;
  font-size: 14px;
  color: var(--text-light-secondary);
}

.login-link a {
  color: var(--accent-color);
  text-decoration: none;
}

.login-link a:hover {
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