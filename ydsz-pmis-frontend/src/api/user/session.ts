/**
 * 会话管理 API
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'

export interface UserSessionVO {
  id?: number
  userId?: number
  username?: string
  sessionId: string
  tokenJti?: string
  loginAt?: string
  lastActiveAt?: string
  expireAt?: string
  clientIp?: string
  userAgent?: string
  deviceType?: string
  /** ACTIVE / KICKED / EXPIRED / LOGOUT */
  status?: string
  logoutAt?: string
  logoutReason?: string
}

/** 我的活跃会话 */
export const listMyActiveSessions = () =>
  request<UserSessionVO[]>({ url: '/user/session/active', method: 'GET' })

/** 下线指定会话 */
export const invalidateSession = (sessionId: string) =>
  request<void>({ url: `/user/session/${sessionId}`, method: 'DELETE' })

/** 下线其他会话 */
export const kickOtherSessions = () =>
  request<number>({ url: '/user/session/others', method: 'DELETE' })

/** 管理员分页查询所有会话 */
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

/** 管理员强制下线任意会话 */
export const adminKickSession = (sessionId: string) =>
  request<void>({ url: `/user/session/admin/${sessionId}`, method: 'DELETE' })
