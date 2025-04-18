<template>
  <div class="setting-container">
    <ParticlesBackground />
    
    <h1 class="setting-title">个人设置</h1>
    
    <div class="setting-card">
      <h2 class="card-title">个人信息</h2>
      <div class="form-item">
        <label>用户名</label>
        <div class="info-value">{{ userInfo.username }}</div>
      </div>
      <div class="form-item">
        <label>年级</label>
        <div class="info-value">{{ userInfo.grade || '未设置' }}</div>
      </div>
      <div class="form-item">
        <label>专业</label>
        <div class="info-value">{{ userInfo.major || '未设置' }}</div>
      </div>
      <div class="form-actions">
        <n-button type="primary" @click="toEditInfo">编辑信息</n-button>
      </div>
    </div>
    
    <div class="setting-card" v-if="showEditForm">
      <h2 class="card-title">编辑个人信息</h2>
      <div class="form-item">
        <label>用户名</label>
        <n-input v-model:value="editForm.username" placeholder="请输入用户名" />
      </div>
      <div class="form-item">
        <label>年级</label>
        <n-input v-model:value="editForm.grade" placeholder="请输入入学年份，如：2021" />
      </div>
      <div class="form-item">
        <label>专业</label>
        <n-input v-model:value="editForm.major" placeholder="请输入专业名称" />
      </div>
      <div class="form-actions">
        <n-button type="primary" @click="updateUserInfo">保存</n-button>
        <n-button @click="cancelEdit" class="cancel-btn">取消</n-button>
      </div>
    </div>
    
    <div class="setting-card">
      <h2 class="card-title">修改密码</h2>
      <div class="form-item">
        <label>旧密码</label>
        <n-input type="password" v-model:value="passwordForm.oldPassword" placeholder="请输入旧密码" />
      </div>
      <div class="form-item">
        <label>新密码</label>
        <n-input type="password" v-model:value="passwordForm.newPassword" placeholder="请输入新密码" />
      </div>
      <div class="form-item">
        <label>确认新密码</label>
        <n-input type="password" v-model:value="passwordForm.confirmPassword" placeholder="请再次输入新密码" />
      </div>
      <div class="form-actions">
        <n-button type="primary" @click="updatePassword">修改密码</n-button>
      </div>
    </div>
    
    <div class="back-action">
      <n-button @click="backToChat">返回聊天</n-button>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { useMessage } from 'naive-ui'
import { NButton, NInput } from 'naive-ui'
import ParticlesBackground from '@/components/ParticlesBackground.vue'

// 使用环境变量获取API基础URL
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

const message = useMessage()
const router = useRouter()

// 用户信息
const userInfo = reactive({
  username: '',
  grade: '',
  major: ''
})

// 编辑表单
const editForm = reactive({
  username: '',
  grade: '',
  major: ''
})

// 是否显示编辑表单
const showEditForm = ref(false)

// 密码表单
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 获取用户信息
const fetchUserInfo = async () => {
  try {
    const token = localStorage.getItem('token')
    if (!token) {
      message.error('请先登录')
      router.push('/login')
      return
    }
    
    const response = await axios.get(`${API_BASE_URL}/api/auth/user-info`, {
      headers: {
        Authorization: `Bearer ${token}`
      }
    })
    
    if (response.data.error === 0) {
      const data = response.data.data
      userInfo.username = data.username
      userInfo.grade = data.grade || ''
      userInfo.major = data.major || ''
    } else {
      message.error(response.data.msg || '获取用户信息失败')
    }
  } catch (error) {
    console.error('获取用户信息失败:', error)
    message.error('获取用户信息失败，请稍后重试')
  }
}

// 显示编辑表单
const toEditInfo = () => {
  // 复制当前用户信息到编辑表单
  editForm.username = userInfo.username
  editForm.grade = userInfo.grade
  editForm.major = userInfo.major
  showEditForm.value = true
}

// 取消编辑
const cancelEdit = () => {
  showEditForm.value = false
}

// 更新用户信息
const updateUserInfo = async () => {
  try {
    const token = localStorage.getItem('token')
    if (!token) {
      message.error('请先登录')
      return
    }
    
    // 简单验证
    if (!editForm.username.trim()) {
      message.error('用户名不能为空')
      return
    }
    
    const response = await axios.put(
      `${API_BASE_URL}/api/auth/user-info`,
      {
        username: editForm.username,
        grade: editForm.grade,
        major: editForm.major
      },
      {
        headers: {
          Authorization: `Bearer ${token}`
        }
      }
    )
    
    if (response.data.error === 0) {
      message.success('个人信息更新成功')
      // 更新显示的信息
      userInfo.username = editForm.username
      userInfo.grade = editForm.grade
      userInfo.major = editForm.major
      // 隐藏编辑表单
      showEditForm.value = false
    } else {
      message.error(response.data.msg || '更新个人信息失败')
    }
  } catch (error) {
    console.error('更新个人信息失败:', error)
    message.error('更新个人信息失败，请稍后重试')
  }
}

// 更新密码
const updatePassword = async () => {
  try {
    const token = localStorage.getItem('token')
    if (!token) {
      message.error('请先登录')
      return
    }
    
    // 密码验证
    if (!passwordForm.oldPassword) {
      message.error('请输入旧密码')
      return
    }
    
    if (!passwordForm.newPassword) {
      message.error('请输入新密码')
      return
    }
    
    if (passwordForm.newPassword.length < 6) {
      message.error('新密码长度不能少于6位')
      return
    }
    
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      message.error('两次输入的新密码不一致')
      return
    }
    
    const response = await axios.put(
      `${API_BASE_URL}/api/auth/password`,
      {
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword
      },
      {
        headers: {
          Authorization: `Bearer ${token}`
        }
      }
    )
    
    if (response.data.error === 0) {
      message.success('密码修改成功')
      // 清空密码表单
      passwordForm.oldPassword = ''
      passwordForm.newPassword = ''
      passwordForm.confirmPassword = ''
    } else {
      message.error(response.data.msg || '密码修改失败')
    }
  } catch (error) {
    console.error('修改密码失败:', error)
    message.error('修改密码失败，请稍后重试')
  }
}

// 返回聊天页面
const backToChat = () => {
  router.push('/')
}

// 页面加载时获取用户信息
onMounted(() => {
  fetchUserInfo()
})
</script>

<style lang="scss" scoped>
.setting-container {
  max-width: 800px;
  margin: 20px auto;
  padding: 20px;
  position: relative;
  z-index: 1;
}

.setting-title {
  font-size: 24px;
  margin-bottom: 20px;
  font-weight: bold;
  color: var(--text-light);
  position: relative;
  z-index: 2;
}

.setting-card {
  background: var(--card-bg-dark);
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.15);
  position: relative;
  z-index: 2;
  border: 1px solid var(--card-border-dark);
  backdrop-filter: blur(5px);
}

.setting-card-title {
  font-size: 18px;
  margin-bottom: 15px;
  color: var(--text-light);
  font-weight: 500;
}

.info-row {
  display: flex;
  margin-bottom: 10px;
  align-items: center;
}

.info-label {
  width: 100px;
  color: var(--text-light-secondary);
}

.info-value {
  flex: 1;
  color: var(--text-light);
}

.password-form {
  margin-top: 15px;
}

:deep(.n-input) {
  background-color: var(--secondary-dark);
}

:deep(.n-input__input) {
  color: var(--text-light);
}

.action-button {
  margin-top: 15px;
}

.edit-form {
  margin-top: 15px;
}

:deep(.n-form-item-label) {
  color: var(--text-light);
}

.button-group {
  margin-top: 15px;
  display: flex;
  gap: 10px;
}

.card-title {
  font-size: 18px;
  margin-bottom: 20px;
  padding-bottom: 10px;
  border-bottom: 1px solid #eee;
}

.form-item {
  margin-bottom: 16px;
  
  label {
    display: block;
    margin-bottom: 8px;
    font-weight: 500;
  }
  
  .info-value {
    font-size: 16px;
    padding: 8px 0;
    color: #333;
  }
}

.form-actions {
  margin-top: 24px;
  
  .cancel-btn {
    margin-left: 10px;
  }
}

.back-action {
  margin-top: 30px;
  text-align: center;
}
</style> 