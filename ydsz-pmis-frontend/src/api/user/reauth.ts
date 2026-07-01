/**
 * 敏感操作二次认证 API
 *
 * 用于需要二次认证的敏感操作（删除用户、重置密码等）。
 * 前端先调用 issueReAuthToken 拿到 token，再把 token 写入
 * X-Re-Auth-Token 请求头调用真正的业务接口。
 */
import { request } from '@/utils/request'

/** 凭据类型 */
export type ReAuthMethod = 'PASSWORD' | 'TOTP' | 'BACKUP_CODE'

export interface ReAuthRequest {
  /** 操作码（与后端 @RequireReAuth.code() 一致） */
  operationCode: string
  /** 凭据类型 */
  method: ReAuthMethod
  /** 当前密码（PASSWORD 时必填） */
  password?: string
  /** 6 位 TOTP 动态码（TOTP 时必填） */
  otp?: string
  /** 8 位备份码（BACKUP_CODE 时必填） */
  backupCode?: string
  /** token 有效期（秒），默认 300，最长 1800 */
  ttlSeconds?: number
}

export interface ReAuthResult {
  /** 二次认证 token（写入 X-Re-Auth-Token 请求头） */
  token: string
  /** 剩余有效期（秒） */
  ttlSeconds: number
  /** 实际凭据类型 */
  method: ReAuthMethod
  /** 操作码 */
  operationCode: string
}

/** 颁发二次认证 token */
export const issueReAuthToken = (data: ReAuthRequest) =>
  request<ReAuthResult>({
    url: '/user/reauth/token',
    method: 'POST',
    data,
  })
