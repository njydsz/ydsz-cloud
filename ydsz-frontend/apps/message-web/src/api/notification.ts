/**
 * 站内通知 API 模块（前端）
 *
 * <p>封装站内消息（{@code ydsz_message_notification}）的 CRUD 接口调用，对应后端
 * {@code /api/v1/message/notifications/*} 端点。供「消息中心 → 通知收件箱」使用。
 *
 * <p><b>核心接口：</b>
 * <ul>
 *   <li>{@link getNotificationPageApi} — 分页查询通知</li>
 *   <li>{@link getNotificationListApi} — 全量查询通知</li>
 *   <li>{@link markReadApi} — 标记单条已读</li>
 *   <li>{@link markAllReadApi} — 全部已读</li>
 *   <li>{@link countUnreadApi} — 未读数</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace NotificationApi {
  /** 通知视图对象 */
  export interface NotificationVO {
    id: string;
    userId: string;
    title: string;
    content: string;
    type: string;
    isRead: number;
    createTime: string;
  }

  /** 通知分页查询条件 */
  export interface NotificationPageQuery {
    pageNum?: number;
    pageSize?: number;
    title?: string;
    type?: string;
  }

  /** 通知传输对象（创建/更新） */
  export interface NotificationDTO {
    userId?: string;
    title?: string;
    content?: string;
    type?: string;
  }
}

/** 分页查询通知 */
export function getNotificationPageApi(params: NotificationApi.NotificationPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: NotificationApi.NotificationVO[];
  }>(`/api/v1/message/notifications/page`, { params });
}

/** 查询全部通知列表 */
export function getNotificationListApi() {
  return requestClient.get<NotificationApi.NotificationVO[]>(`/api/v1/message/notifications/list`);
}

/** 根据 ID 查询 */
export function getNotificationByIdApi(id: string) {
  return requestClient.get<NotificationApi.NotificationVO>(`/api/v1/message/notifications/${id}`);
}

/** 创建 */
export function createNotificationApi(data: NotificationApi.NotificationDTO) {
  return requestClient.post<string>(`/api/v1/message/notifications`, data);
}

/** 更新 */
export function updateNotificationApi(data: NotificationApi.NotificationDTO) {
  return requestClient.put<boolean>(`/api/v1/message/notifications`, data);
}

/** 删除 */
export function deleteNotificationApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/message/notifications/${id}`);
}
