import type { LoginParams, LoginResult, UserInfo } from './types'

export * from './types'

/**
 * 登录
 */
export const loginApi = (data: LoginParams) =>
  request<LoginResult>({ url: '/auth/login', method: 'POST', data })

/**
 * 登出
 */
export const logoutApi = () => request<void>({ url: '/auth/logout', method: 'POST' })

/**
 * 获取当前用户信息
 */
export const getUserInfoApi = () => request<UserInfo>({ url: '/users/me', method: 'GET' })

/**
 * 刷新 Token
 */
export const refreshTokenApi = (refreshToken: string) =>
  request<LoginResult>({ url: '/auth/refresh', method: 'POST', params: { refreshToken } })

import { request } from '@/utils/request'
