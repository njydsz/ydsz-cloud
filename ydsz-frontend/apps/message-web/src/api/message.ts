/**
 * 消息 API 模块（前端）
 * <p>封装消息（{@code ydsz_message_log}）查询接口，对应后端 {@code /api/v1/message/*} 端点。
 * <p>支持站内/邮件/短信/企微/钉钉/飞书多渠道消息的发送记录查询。
 * <p>供「消息中心 → 发送历史」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace MessageApi {
  /** 消息发送记录视图对象 */
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

  /** 消息记录分页查询参数 */
  export interface MessagePageQuery {
    pageNum?: number;
    pageSize?: number;
    channel?: string;
    status?: string;
  }

  /** 消息发送请求参数 */
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
