import { requestClient } from '#/api/request';

export namespace MessageApi {
  export interface MessageVO {
    id: string;
    messageId: string;
    channel: string;
    recipient: string;
    subject: string;
    content: string;
    status: string;
    sendTime: string;
    createTime: string;
  }

  export interface MessagePageQuery {
    pageNum?: number;
    pageSize?: number;
    channel?: string;
    status?: string;
  }

  export interface MessageDTO {
    channel?: string;
    recipient?: string;
    subject?: string;
    content?: string;
  }
}

/** 分页查询 */
export function getMessagePageApi(params: MessageApi.MessagePageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: MessageApi.MessageVO[];
  }>(`/api/v1/message/page`, { params });
}

/** 查询全部列表 */
export function getMessageListApi() {
  return requestClient.get<MessageApi.MessageVO[]>(`/api/v1/message/list`);
}

/** 根据 ID 查询 */
export function getMessageByIdApi(id: string) {
  return requestClient.get<MessageApi.MessageVO>(`/api/v1/message/${id}`);
}

/** 创建 */
export function createMessageApi(data: MessageApi.MessageDTO) {
  return requestClient.post<string>(`/api/v1/message`, data);
}

/** 更新 */
export function updateMessageApi(data: MessageApi.MessageDTO) {
  return requestClient.put<boolean>(`/api/v1/message`, data);
}

/** 删除 */
export function deleteMessageApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/message/${id}`);
}
