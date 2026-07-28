/**
 * 消息模板 API 模块（前端）
 * <p>封装消息模板（{@code ydsz_message_template}）CRUD 接口，对应后端 {@code /api/v1/message/template/*} 端点。
 * <p>支持多渠道（站内/邮件/短信/企微/钉钉/飞书）模板管理、变量替换、状态控制。
 * <p>供「消息中心 → 模板管理」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace TemplateApi {
  export interface TemplateVO {
    id: string;
    templateCode: string;
    templateName: string;
    channel: string;
    subject: string;
    content: string;
    status: number;
    createTime: string;
  }

  export interface TemplatePageQuery {
    pageNum?: number;
    pageSize?: number;
    templateName?: string;
    templateCode?: string;
  }

  export interface TemplateDTO {
    templateCode?: string;
    templateName?: string;
    channel?: string;
    subject?: string;
    content?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getTemplatePageApi(params: TemplateApi.TemplatePageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: TemplateApi.TemplateVO[];
  }>(`/api/v1/message/template/page`, { params });
}

/** 查询全部列表 */
export function getTemplateListApi() {
  return requestClient.get<TemplateApi.TemplateVO[]>(`/api/v1/message/template/list`);
}

/** 根据 ID 查询 */
export function getTemplateByIdApi(id: string) {
  return requestClient.get<TemplateApi.TemplateVO>(`/api/v1/message/template/${id}`);
}

/** 创建 */
export function createTemplateApi(data: TemplateApi.TemplateDTO) {
  return requestClient.post<string>(`/api/v1/message/template`, data);
}

/** 更新 */
export function updateTemplateApi(data: TemplateApi.TemplateDTO) {
  return requestClient.put<boolean>(`/api/v1/message/template`, data);
}

/** 删除 */
export function deleteTemplateApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/message/template/${id}`);
}
