/**
 * notification API 接口定义
 *
 * @path main\src\api\core\notification.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace NotificationApi {
  export interface NotificationItem {
    id: string;
    title: string;
    message: string;
    type: string;
    isRead: boolean;
    createdAt: string;
    avatar?: string;
    link?: string;
  }

  export interface NotificationPageQuery {
    pageNum?: number;
    pageSize?: number;
    isRead?: boolean;
    type?: string;
  }
}

/** 分页查询通知列表 */
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

/** 获取未读通知数量 */
export function getUnreadCountApi() {
  return requestClient.get<number>('/api/v1/notification/unread-count');
}

/** 标记通知为已读 */
export function markAsReadApi(id: string) {
  return requestClient.put<boolean>(`/api/v1/notification/${id}/read`);
}

/** 全部标记为已读 */
export function markAllAsReadApi() {
  return requestClient.put<boolean>('/api/v1/notification/read-all');
}

/** 删除通知 */
export function deleteNotificationApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/notification/${id}`);
}

/** 清空全部通知 */
export function clearAllNotificationsApi() {
  return requestClient.delete<boolean>('/api/v1/notification/clear-all');
}
