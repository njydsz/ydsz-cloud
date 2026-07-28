/**
 * 流程模板 API 模块（前端）
 * <p>封装流程模板（{@code ydsz_flow_definition}）CRUD 接口，对应后端 {@code /api/v1/workflow/template/*} 端点。
 * <p>支持 BPMN 2.0 标准、可视化设计、版本管理、灰度发布。
 * <p>供「工作流 → 流程模板」使用。
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
    category: string;
    version: string;
    description: string;
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
    category?: string;
    description?: string;
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
  }>(`/api/v1/workflow/template/page`, { params });
}

/** 查询全部列表 */
export function getTemplateListApi() {
  return requestClient.get<TemplateApi.TemplateVO[]>(`/api/v1/workflow/template/list`);
}

/** 根据 ID 查询 */
export function getTemplateByIdApi(id: string) {
  return requestClient.get<TemplateApi.TemplateVO>(`/api/v1/workflow/template/${id}`);
}

/** 创建 */
export function createTemplateApi(data: TemplateApi.TemplateDTO) {
  return requestClient.post<string>(`/api/v1/workflow/template`, data);
}

/** 更新 */
export function updateTemplateApi(data: TemplateApi.TemplateDTO) {
  return requestClient.put<boolean>(`/api/v1/workflow/template`, data);
}

/** 删除 */
export function deleteTemplateApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/workflow/template/${id}`);
}
