export * from './env'

// API基础URL，根据环境配置
export const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'
