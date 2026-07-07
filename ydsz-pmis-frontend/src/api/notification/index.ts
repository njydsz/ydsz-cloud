/**
 * @file 通知中心 API 接口封装
 * @description 提供收件箱分页、未读数、标记已读、全部已读、发送通知、删除通知等能力，
 *              对应后端 NotificationController（/notifications）。
 *
 *   - 路径前缀  由 VITE_API_PREFIX 注入，此处 BASE 仅写 /notifications
 *   - 收件箱/未读数/标记已读等后台静默请求，不触发全局 loading
 * @module api/notification
 */
import { request } from '@/utils/request'
import type { PageData } from '@/types/api'
import type { NotificationVO, NotificationSendDTO, NotificationPageQuery } from './types'

/** 通知中心接口路径（ 前缀由 baseURL 注入） */
const BASE = '/notifications'

/**
 * 查询收件箱（分页）
 * @param params 分页与筛选条件
 * @returns 通知分页数据
 */
export const getInbox = (params: NotificationPageQuery) =>
  request<PageData<NotificationVO>>({
    url: `${BASE}/inbox`,
    method: 'GET',
    params,
    silent: true,
  })

/**
 * 查询当前用户未读通知数
 * @returns 未读数量
 */
export const getUnreadCount = () =>
  request<number>({ url: `${BASE}/unread-count`, method: 'GET', silent: true })

/**
 * 标记单条通知为已读
 * @param id 通知 ID
 */
export const markRead = (id: number) =>
  request<void>({ url: `${BASE}/${id}/read`, method: 'POST', silent: true })

/**
 * 标记全部通知为已读
 */
export const markAllRead = () =>
  request<void>({ url: `${BASE}/read-all`, method: 'POST', silent: true })

/**
 * 发送通知（站内信，可选邮件）
 * @param data 通知发送参数
 * @returns 成功发送条数
 */
export const sendNotification = (data: NotificationSendDTO) =>
  request<number>({ url: `${BASE}/send`, method: 'POST', data })

/**
 * 删除通知（支持批量）
 * @param ids 通知 ID 列表（后端 @RequestBody List<Long>）
 */
export const deleteNotifications = (ids: number[]) =>
  request<void>({ url: BASE, method: 'DELETE', data: ids })
