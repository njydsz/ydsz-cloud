/**
 * notification API 接口定义
 *
 * @path main\src\api\core\notification.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace NotificationApi {
  /** 通知列表项。 */
  export interface NotificationItem {
    /** 通知唯一 ID */
    id: string;
    /** 通知标题 */
    title: string;
    /** 通知正文内容 */
    message: string;
    /** 通知类型（如 system / todo，用于前端分类展示） */
    type: string;
    /** 是否已读 */
    isRead: boolean;
    /** 创建时间（ISO 字符串） */
    createdAt: string;
    /** 发送者头像 URL，可选 */
    avatar?: string;
    /** 点击通知跳转的链接，可选 */
    link?: string;
  }

  /** 通知分页查询参数。 */
  export interface NotificationPageQuery {
    /** 页码，从 1 开始 */
    pageNum?: number;
    /** 每页条数 */
    pageSize?: number;
    /** 按已读状态过滤，可选 */
    isRead?: boolean;
    /** 按通知类型过滤，可选 */
    type?: string;
  }
}

/**
 * 分页查询通知列表。
 *
 * @param params - 分页与过滤条件（页码、条数、已读状态、类型）
 * @returns 分页结果，含 total / current / size 与通知条目数组
 */
export function getNotificationsApi(
  params: NotificationApi.NotificationPageQuery,
) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: NotificationApi.NotificationItem[];
  }>('/api/v1/notification/page', { params });
}

/**
 * 获取未读通知数量。
 *
 * @returns 未读通知总数
 */
export function getUnreadCountApi() {
  return requestClient.get<number>('/api/v1/notification/unread-count');
}

/**
 * 将指定通知标记为已读。
 *
 * @param id - 通知 ID
 * @returns 是否标记成功
 */
export function markAsReadApi(id: string) {
  return requestClient.put<boolean>(`/api/v1/notification/${id}/read`);
}

/**
 * 将所有通知标记为已读。
 *
 * @returns 是否操作成功
 */
export function markAllAsReadApi() {
  return requestClient.put<boolean>('/api/v1/notification/read-all');
}

/**
 * 删除指定通知。
 *
 * @param id - 通知 ID
 * @returns 是否删除成功
 */
export function deleteNotificationApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/notification/${id}`);
}

/**
 * 清空当前用户全部通知。
 *
 * @returns 是否操作成功
 */
export function clearAllNotificationsApi() {
  return requestClient.delete<boolean>('/api/v1/notification/clear-all');
}
