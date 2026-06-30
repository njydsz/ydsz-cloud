/**
 * 双因素认证 (2FA / TOTP) API
 */
import { request } from '@/utils/request'

export interface TwoFactorBindResult {
  /** 密钥（Base32），前端生成 otpauth URI */
  secret: string
  /** otpauth URI，可直接渲染为二维码 */
  otpauthUri: string
}

export interface TwoFactorStatusVO {
  /** 是否已绑定 */
  enabled: boolean
  /** 绑定时间 */
  boundAt?: string
  /** 上次使用时间 */
  lastUsedAt?: string
  /** 备份码剩余数量 */
  backupCodeCount?: number
}

/** 发起 TOTP 绑定（返回密钥 + URI） */
export const bind2fa = () =>
  request<TwoFactorBindResult>({ url: '/user/2fa/bind', method: 'POST' })

/** 校验 OTP 完成绑定 */
export const confirm2fa = (otp: string) =>
  request<boolean>({ url: '/user/2fa/confirm', method: 'POST', params: { otp } })

/** 校验 2FA（用于登录第二步） */
export const verify2fa = (otp: string) =>
  request<boolean>({ url: '/user/2fa/verify', method: 'POST', params: { otp } })

/** 使用备份码 */
export const verifyBackupCode = (code: string) =>
  request<boolean>({ url: '/user/2fa/verify-backup', method: 'POST', params: { code } })

/** 关闭 2FA */
export const disable2fa = () =>
  request<void>({ url: '/user/2fa/disable', method: 'POST' })

/** 查询我的 2FA 状态 */
export const get2faStatus = () =>
  request<TwoFactorStatusVO>({ url: '/user/2fa/me', method: 'GET' })

/** 查询备份码（脱敏） */
export const listBackupCodes = () =>
  request<string[]>({ url: '/user/2fa/backup-codes', method: 'GET' })
