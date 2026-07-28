/**
 * 死信队列 API 模块（前端）
 * <p>封装死信队列（{@code ydsz_message_dead_letter}）接口，对应后端 {@code /api/v1/message/deadLetter/*} 端点。
 * <p>查询/重投/丢弃发送失败超过阈值次数的消息。
 * <p>供「消息中心 → 死信管理」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
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
  }>(`/api/v1/message/deadLetter/page`, { params });
}

/** 查询全部列表 */
export function getDeadLetterListApi() {
  return requestClient.get<DeadLetterApi.DeadLetterVO[]>(`/api/v1/message/deadLetter/list`);
}

/** 根据 ID 查询 */
export function getDeadLetterByIdApi(id: string) {
  return requestClient.get<DeadLetterApi.DeadLetterVO>(`/api/v1/message/deadLetter/${id}`);
}

/** 创建 */
export function createDeadLetterApi(data: DeadLetterApi.DeadLetterDTO) {
  return requestClient.post<string>(`/api/v1/message/deadLetter`, data);
}

/** 更新 */
export function updateDeadLetterApi(data: DeadLetterApi.DeadLetterDTO) {
  return requestClient.put<boolean>(`/api/v1/message/deadLetter`, data);
}

/** 删除 */
export function deleteDeadLetterApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/message/deadLetter/${id}`);
}
