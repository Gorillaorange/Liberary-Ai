/**
 * 登录请求参数
 */
export interface LoginRequest {
  /**
   * 用户名
   */
  username: string;
  
  /**
   * 密码
   */
  password: string;
  
  /**
   * 是否记住我
   */
  rememberMe?: boolean;
}

/**
 * 注册请求参数
 */
export interface RegisterRequest {
  /**
   * 用户名
   */
  username: string;
  
  /**
   * 密码
   */
  password: string;
  
  /**
   * 确认密码
   */
  confirmPassword: string;
  
  /**
   * 年级，如：2021
   */
  grade?: string;
  
  /**
   * 专业
   */
  major?: string;
}

/**
 * 登录响应数据的内部数据结构
 */
export interface LoginData {
  /**
   * 访问令牌
   */
  token: string;
  
  /**
   * 用户名
   */
  username: string;
}

/**
 * 登录响应
 */
export interface LoginResponse {
  /**
   * 错误码，0表示成功
   */
  error: number;
  
  /**
   * 错误信息
   */
  msg: string;
  
  /**
   * 响应数据
   */
  data?: LoginData;
}

/**
 * 注册响应
 */
export interface RegisterResponse {
  /**
   * 错误码
   */
  error: number;
  
  /**
   * 错误信息
   */
  msg: string;
  
  /**
   * 数据
   */
  data?: {
    /**
     * 用户名
     */
    username: string;
    
    /**
     * 年级
     */
    grade?: string;
    
    /**
     * 专业
     */
    major?: string;
  };
} 