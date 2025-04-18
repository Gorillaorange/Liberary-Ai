import request from './request'
import type { LoginRequest, LoginResponse, RegisterRequest, RegisterResponse } from '@/types/auth'
import { API_BASE_URL } from '@/config'
import axios from 'axios'

/**
 * 用户登录
 */
export function login(data: LoginRequest): Promise<any> {
  console.log('登录请求参数:', data)
  console.log('登录请求地址:', `${API_BASE_URL}/api/auth/login`)
  return axios.post(`${API_BASE_URL}/api/auth/login`, data)
    .then(response => {
      console.log('原始登录响应:', JSON.stringify(response));
      return response;
    });
}

/**
 * 用户注册
 */
export function register(data: RegisterRequest): Promise<RegisterResponse> {
  console.log('注册请求参数:', data)
  // 修复：返回后端的 data 字段
  return axios.post(`${API_BASE_URL}/api/auth/register`, data).then(res => res.data)
}

/**
 * 退出登录
 */
export function logout(): Promise<any> {
  return axios.post(`${API_BASE_URL}/api/auth/logout`, null, {
    headers: {
      Authorization: `Bearer ${localStorage.getItem('token')}`
    }
  })
}

/**
 * 获取用户信息
 */
export function getUserInfo(): Promise<any> {
  return axios.get(`${API_BASE_URL}/api/auth/user-info`, {
    headers: {
      Authorization: `Bearer ${localStorage.getItem('token')}`
    }
  })
}