/**
 * @file 用户认证与当前用户信息 API
 * @description 封装登录、登出、刷新 Token、获取/修改当前用户信息等接口，
 *              对应后端 AuthController（/auth/**）与 UserController（/users/me/**）。
 * @module api/user
 */
import type { LoginParams, LoginResult, UserInfo } from './types'

export * from './types'

/**
 * 获取图形验证码
 *
 * 用于登录页展示图形验证码，后端返回 captchaKey（用于后续登录校验）与 captchaImage（Base64 图片）。
 *
 * @returns 包含 captchaKey 与 captchaImage（Base64）的对象
 */
export const getCaptchaApi = () =>
  request<{ captchaKey: string; captchaImage: string }>({ url: '/auth/captcha', method: 'GET' })

/**
 * 登录
 *
 * 提交用户名密码及验证码进行登录，可能返回 accessToken 或 mfaRequired=true（需走 2FA 二次验证）。
 *
 * @param data 登录参数（用户名、密码、验证码、可选 2FA 码）
 * @returns 登录结果（含 accessToken / refreshToken / mfaRequired 等）
 */
export const loginApi = (data: LoginParams) =>
  request<LoginResult>({ url: '/auth/login', method: 'POST', data })

/**
 * 登出
 *
 * 注销当前会话并使 accessToken 失效。
 *
 * @returns void
 */
export const logoutApi = () => request<void>({ url: '/auth/logout', method: 'POST' })

/**
 * 获取当前用户信息
 *
 * 拉取当前登录用户的资料、角色与权限码列表。
 *
 * 注：此接口由路由守卫在首次进入受保护路由时调用，设置 skipCancel: true
 * 避免路由切换时被 cancelAll 打断导致用户被误判为未登录。
 *
 * @returns 当前用户信息
 */
export const getUserInfoApi = () =>
  request<UserInfo>({ url: '/users/me', method: 'GET', skipCancel: true })

/**
 * 刷新 Token
 *
 * 使用 refreshToken 换取新的 accessToken，用于无感续期。
 *
 * @param refreshToken 登录时下发的 refreshToken
 * @returns 新的登录结果（含新的 accessToken / refreshToken）
 */
export const refreshTokenApi = (refreshToken: string) =>
  request<LoginResult>({
    url: '/auth/refresh',
    method: 'POST',
    params: { refreshToken },
    // 标记为刷新请求：跳过响应拦截器的无感刷新逻辑，避免递归
    _isRefreshTokenRequest: true,
    // 刷新请求不展示全局 loading
    silent: true,
  })

/**
 * 修改自己的密码
 *
 * 当前用户在已登录状态下修改自身密码，需校验旧密码。
 *
 * @param data 含 oldPassword 与 newPassword
 * @returns void
 */
export const changePasswordApi = (data: { oldPassword: string; newPassword: string }) =>
  request<void>({ url: '/users/me/password', method: 'POST', data })

import { request } from '@/utils/request'
