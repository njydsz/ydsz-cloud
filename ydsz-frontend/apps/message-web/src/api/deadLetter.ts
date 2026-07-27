import { requestClient } from '#/api/request';

export namespace DeadLetterApi {
  export interface DeadLetterVO {
    id: string;
    messageId: string;
    channel: string;
    errorMessage: string;
    retryCount: number;
    status: string;
    createTime: string;
  }

  export interface DeadLetterPageQuery {
    pageNum?: number;
    pageSize?: number;
    messageId?: string;
  }

  export interface DeadLetterDTO {
    messageId?: string;
    channel?: string;
    errorMessage?: string;
  }
}

/** 分页查询 */
export function getDeadLetterPageApi(params: DeadLetterApi.DeadLetterPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: DeadLetterApi.DeadLetterVO[];
  }>(`/message/deadLetter/page`, { params });
}

/** 查询全部列表 */
export function getDeadLetterListApi() {
  return requestClient.get<DeadLetterApi.DeadLetterVO[]>(`/message/deadLetter/list`);
}

/** 根据 ID 查询 */
export function getDeadLetterByIdApi(id: string) {
  return requestClient.get<DeadLetterApi.DeadLetterVO>(`/message/deadLetter/${id}`);
}

/** 创建 */
export function createDeadLetterApi(data: DeadLetterApi.DeadLetterDTO) {
  return requestClient.post<string>(`/message/deadLetter`, data);
}

/** 更新 */
export function updateDeadLetterApi(data: DeadLetterApi.DeadLetterDTO) {
  return requestClient.put<boolean>(`/message/deadLetter`, data);
}

/** 删除 */
export function deleteDeadLetterApi(id: string) {
  return requestClient.delete<boolean>(`/message/deadLetter/${id}`);
}
