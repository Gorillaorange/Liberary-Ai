/**
 * 令牌处理工具函数
 */

/**
 * 获取格式化后的令牌
 * 处理并返回适合 Authorization 头的令牌，确保格式正确且无空格等无效字符
 * 
 * @returns 格式化后的令牌，如果未登录则返回null
 */
export function getFormattedToken(): string | null {
  const token = localStorage.getItem('token')
  
  if (!token) {
    return null
  }
  
  // 清理并格式化令牌：移除首尾空格
  const cleanToken = token.trim()
  
  // 确保令牌使用 Bearer 前缀
  return !cleanToken.startsWith('Bearer ') ? `Bearer ${cleanToken}` : cleanToken
}

/**
 * 获取令牌，不带 Bearer 前缀
 * 
 * @returns 不带前缀的令牌，如果未登录则返回null
 */
export function getRawToken(): string | null {
  const token = localStorage.getItem('token')
  
  if (!token) {
    return null
  }
  
  // 清理令牌：移除首尾空格
  const cleanToken = token.trim()
  
  // 移除 Bearer 前缀如果有的话
  return cleanToken.startsWith('Bearer ') 
    ? cleanToken.substring(7).trim() 
    : cleanToken
}

/**
 * 检查用户是否已登录
 * 
 * @returns 是否已登录
 */
export function isLoggedIn(): boolean {
  return !!getFormattedToken()
}

/**
 * 保存令牌到 localStorage
 * 
 * @param token 要保存的令牌
 */
export function saveToken(token: string): void {
  if (token) {
    // 确保令牌格式正确
    const cleanToken = token.trim()
    localStorage.setItem('token', cleanToken)
  }
}

/**
 * 清除令牌
 */
export function clearToken(): void {
  localStorage.removeItem('token')
} 