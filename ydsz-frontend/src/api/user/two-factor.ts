/**
 * @file 双因素认证 (2FA / TOTP) API
 * @description 提供绑定/校验/关闭 2FA、查询状态与备份码等能力，
 *              对应后端 TwoFactorAuthController（/user/2fa/**）。
 * @module api/user/two-factor
 */
import { request } from '@/utils/request'

/**
 * 2FA 绑定结果
 */
export interface TwoFactorBindResult {
  /** 密钥（Base32），前端生成 otpauth URI */
  secret: string
  /** otpauth URI，可直接渲染为二维码 */
  otpauthUri: string
}

/**
 * 2FA 状态视图
 */
export interface TwoFactorStatusVO {
  /** 是否已绑定 */
  enabled: boolean
  /** 绑定时间（ISO 8601） */
  boundAt?: string
  /** 上次使用时间（ISO 8601） */
  lastUsedAt?: string
  /** 备份码剩余数量 */
  backupCodeCount?: number
}

/**
 * 发起 TOTP 绑定（返回密钥 + URI）
 *
 * 后端生成密钥并返回 otpauth URI，前端渲染二维码供用户用 Authenticator 扫码。
 *
 * @returns 包含 secret 与 otpauthUri 的绑定结果
 */
export const bind2fa = () =>
  request<TwoFactorBindResult>({ url: '/user/2fa/bind', method: 'POST' })

/**
 * 校验 OTP 完成绑定
 *
 * 用户输入首次动态码以确认绑定生效。
 *
 * @param otp 6 位 TOTP 动态码
 * @returns 是否绑定成功
 */
export const confirm2fa = (otp: string) =>
  request<boolean>({ url: '/user/2fa/confirm', method: 'POST', params: { otp } })

/**
 * 校验 2FA（用于登录第二步）
 *
 * 登录命中 mfaRequired 后，调用此接口完成二次验证。
 *
 * @param otp 6 位 TOTP 动态码
 * @returns 是否校验通过
 */
export const verify2fa = (otp: string) =>
  request<boolean>({ url: '/user/2fa/verify', method: 'POST', params: { otp } })

/**
 * 使用备份码
 *
 * 当用户无法获取 TOTP 时，可使用一次性备份码完成 2FA。
 *
 * @param code 8 位备份码
 * @returns 是否校验通过
 */
export const verifyBackupCode = (code: string) =>
  request<boolean>({ url: '/user/2fa/verify-backup', method: 'POST', params: { code } })

/**
 * 关闭 2FA
 *
 * 关闭当前用户的 2FA 绑定，需配合二次认证。
 *
 * @returns void
 */
export const disable2fa = () =>
  request<void>({ url: '/user/2fa/disable', method: 'POST' })

/**
 * 查询我的 2FA 状态
 *
 * 返回当前用户 2FA 是否启用、绑定时间、备份码剩余数量等。
 *
 * @returns 2FA 状态视图
 */
export const get2faStatus = () =>
  request<TwoFactorStatusVO>({ url: '/user/2fa/me', method: 'GET' })

/**
 * 查询备份码（脱敏）
 *
 * 返回当前用户剩余的备份码列表（已脱敏展示）。
 *
 * @returns 备份码字符串数组
 */
export const listBackupCodes = () =>
  request<string[]>({ url: '/user/2fa/backup-codes', method: 'GET' })
