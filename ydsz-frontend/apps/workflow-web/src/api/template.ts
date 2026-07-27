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
  }>(`/workflow/template/page`, { params });
}

/** 查询全部列表 */
export function getTemplateListApi() {
  return requestClient.get<TemplateApi.TemplateVO[]>(`/workflow/template/list`);
}

/** 根据 ID 查询 */
export function getTemplateByIdApi(id: string) {
  return requestClient.get<TemplateApi.TemplateVO>(`/workflow/template/${id}`);
}

/** 创建 */
export function createTemplateApi(data: TemplateApi.TemplateDTO) {
  return requestClient.post<string>(`/workflow/template`, data);
}

/** 更新 */
export function updateTemplateApi(data: TemplateApi.TemplateDTO) {
  return requestClient.put<boolean>(`/workflow/template`, data);
}

/** 删除 */
export function deleteTemplateApi(id: string) {
  return requestClient.delete<boolean>(`/workflow/template/${id}`);
}
