/**
 * @file 会话管理 API
 * @description 提供当前用户活跃会话查询、自助下线会话，以及管理员分页查询、强制下线任意会话等能力。
 *              对应后端 UserSessionController（/user/session/**）。
 * @module api/user/session
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'

/**
 * 用户会话视图对象
 */
export interface UserSessionVO {
  /** 会话主键 ID */
  id?: number
  /** 用户 ID */
  userId?: number
  /** 用户名 */
  username?: string
  /** 会话 ID（JWT 的 jti） */
  sessionId: string
  /** token 的 jti 声明 */
  tokenJti?: string
  /** 登录时间（ISO 8601） */
  loginAt?: string
  /** 最后活跃时间（ISO 8601） */
  lastActiveAt?: string
  /** 过期时间（ISO 8601） */
  expireAt?: string
  /** 客户端 IP */
  clientIp?: string
  /** User-Agent */
  userAgent?: string
  /** 设备类型（PC / MOBILE / ...） */
  deviceType?: string
  /** ACTIVE / KICKED / EXPIRED / LOGOUT */
  status?: string
  /** 登出时间（ISO 8601） */
  logoutAt?: string
  /** 登出原因 */
  logoutReason?: string
}

/**
 * 我的活跃会话
 *
 * 拉取当前登录用户的全部活跃会话列表（含当前会话）。
 *
 * @returns 活跃会话列表
 */
export const listMyActiveSessions = () =>
  request<UserSessionVO[]>({ url: '/user/session/active', method: 'GET' })

/**
 * 下线指定会话
 *
 * 当前用户主动下线自己的一条会话（不能下线他人会话）。
 *
 * @param sessionId 会话 ID
 * @returns void
 */
export const invalidateSession = (sessionId: string) =>
  request<void>({ url: `/user/session/${sessionId}`, method: 'DELETE' })

/**
 * 下线其他会话
 *
 * 下线当前用户除当前会话外的全部其他会话，常用于"在其他设备登出"。
 *
 * @returns 被下线的会话数量
 */
export const kickOtherSessions = () =>
  request<number>({ url: '/user/session/others', method: 'DELETE' })

/**
 * 管理员分页查询所有会话
 *
 * 管理员可按用户、状态、IP 维度筛选全站会话，用于安全审计。
 *
 * @param page 页码（从 0 或 1 起，遵循后端约定）
 * @param size 每页大小
 * @param params 可选筛选条件（userId / status / clientIp）
 * @returns 会话分页结果
 */
export const adminPageSessions = (
  page: number,
  size: number,
  params?: { userId?: number; status?: string; clientIp?: string },
) =>
  request<PageResult<UserSessionVO>>({
    url: '/user/session/admin/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/**
 * 管理员强制下线任意会话
 *
 * 管理员强制下线指定会话（不限于自身），状态置为 KICKED。
 *
 * @param sessionId 会话 ID
 * @returns void
 */
export const adminKickSession = (sessionId: string) =>
  request<void>({ url: `/user/session/admin/${sessionId}`, method: 'DELETE' })
