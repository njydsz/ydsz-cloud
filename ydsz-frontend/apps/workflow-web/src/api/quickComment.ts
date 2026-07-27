import { requestClient } from '#/api/request';

export namespace QuickCommentApi {
  export interface QuickCommentVO {
    id: string;
    content: string;
    category: string;
    sort: number;
    status: number;
    createTime: string;
  }

  export interface QuickCommentPageQuery {
    pageNum?: number;
    pageSize?: number;
    content?: string;
  }

  export interface QuickCommentDTO {
    content?: string;
    category?: string;
    sort?: number;
    status?: number;
  }
}

/** 分页查询 */
export function getQuickCommentPageApi(params: QuickCommentApi.QuickCommentPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: QuickCommentApi.QuickCommentVO[];
  }>(`/api/workflow/quickComments/page`, { params });
}

/** 查询全部列表 */
export function getQuickCommentListApi() {
  return requestClient.get<QuickCommentApi.QuickCommentVO[]>(`/api/workflow/quickComments/list`);
}

/** 根据 ID 查询 */
export function getQuickCommentByIdApi(id: string) {
  return requestClient.get<QuickCommentApi.QuickCommentVO>(`/api/workflow/quickComments/${id}`);
}

/** 创建 */
export function createQuickCommentApi(data: QuickCommentApi.QuickCommentDTO) {
  return requestClient.post<string>(`/api/workflow/quickComments`, data);
}

/** 更新 */
export function updateQuickCommentApi(data: QuickCommentApi.QuickCommentDTO) {
  return requestClient.put<boolean>(`/api/workflow/quickComments`, data);
}

/** 删除 */
export function deleteQuickCommentApi(id: string) {
  return requestClient.delete<boolean>(`/api/workflow/quickComments/${id}`);
}
