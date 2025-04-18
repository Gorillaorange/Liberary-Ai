import request from '@/utils/request'
import * as authAPI from './auth'

/**
 * Get 请求示例
 */
export function getXxxxPrompt (params) {
  return request.get(`/xxxxxx/test/prompt`, params)
}

// 导出身份认证相关API
export { authAPI }

