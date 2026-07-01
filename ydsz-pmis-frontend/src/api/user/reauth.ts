/**
 * @file 敏感操作二次认证 API
 * @description 用于需要二次认证的敏感操作（删除用户、重置密码等）。
 *              前端先调用 issueReAuthToken 拿到 token，再把 token 写入
 *              X-Re-Auth-Token 请求头调用真正的业务接口。
 *              对应后端 ReAuthController（/user/reauth/**）。
 * @module api/user/reauth
 */
import { request } from '@/utils/request'

/** 凭据类型：密码 / TOTP 动态码 / 备份码 */
export type ReAuthMethod = 'PASSWORD' | 'TOTP' | 'BACKUP_CODE'

/**
 * 二次认证请求参数
 */
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

/**
 * 二次认证返回结果
 */
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

/**
 * 颁发二次认证 token
 *
 * 校验凭据通过后颁发短期 token，业务接口通过 X-Re-Auth-Token 头携带该 token 完成敏感操作。
 *
 * @param data 二次认证请求参数（操作码、凭据类型、凭据值）
 * @returns 二次认证 token 及有效期信息
 */
export const issueReAuthToken = (data: ReAuthRequest) =>
  request<ReAuthResult>({
    url: '/user/reauth/token',
    method: 'POST',
    data,
  })
