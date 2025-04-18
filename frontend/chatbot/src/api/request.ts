import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { useRouter } from 'vue-router'

// 创建axios实例
const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api', // 设置API基础路径
  timeout: 300000, // 请求超时时间（300秒，5分钟）
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 获取token
    const token = localStorage.getItem('token')
    // 如果有token则添加到请求头
    if (token && config.headers) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  (response: AxiosResponse) => {
    // 处理特殊响应头
    if (response.headers['clear-auth'] === 'true') {
      // 服务器要求清除认证信息
      clearAuthData()
    }
    return response.data
  },
  (error) => {
    if (error.response) {
      const status = error.response.status
      
      // 401未授权，清除token并跳转到登录页
      if (status === 401) {
        clearAuthData()
        window.location.href = '/login'
      }
      
      // 处理其他错误
      if (status === 403) {
        console.error('没有权限访问')
      }
    }
    return Promise.reject(error)
  }
)

// 清除所有认证相关数据
function clearAuthData() {
  // 清除token和用户信息
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  // 不再需要移除userId，因为已经不再存储
  // 清除可能存在的其他用户相关信息
  sessionStorage.clear()
}

export default request 