import { requestClient } from '#/api/request';

export namespace NotificationApi {
  export interface NotificationVO {
    id: string;
    userId: string;
    title: string;
    content: string;
    type: string;
    isRead: number;
    createTime: string;
  }

  export interface NotificationPageQuery {
    pageNum?: number;
    pageSize?: number;
    title?: string;
    type?: string;
  }

  export interface NotificationDTO {
    userId?: string;
    title?: string;
    content?: string;
    type?: string;
  }
}

/** 分页查询 */
export function getNotificationPageApi(params: NotificationApi.NotificationPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: NotificationApi.NotificationVO[];
  }>(`/api/v1/message/notifications/page`, { params });
}

/** 查询全部列表 */
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
