/**
 * 快捷回复 API 模块（前端）
 * <p>封装审批快捷回复模板接口，对应后端 {@code /api/v1/workflow/quickComment/*} 端点。
 * <p>支持预设常用审批意见，按分类管理。
 * <p>供「工作流 → 审批面板」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace QuickCommentApi {
  /** 快捷回复模板视图对象 */
  export interface QuickCommentVO {
    id: string;
    content: string;
    category: string;
    sort: number;
    status: number;
    createTime: string;
  }

  /** 快捷回复分页查询参数 */
  export interface QuickCommentPageQuery {
    pageNum?: number;
    pageSize?: number;
    content?: string;
  }

  /** 快捷回复创建/更新请求参数 */
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
  }>(`/api/v1/workflow/quickComments/page`, { params });
}

/** 查询全部列表 */
export function getQuickCommentListApi() {
  return requestClient.get<QuickCommentApi.QuickCommentVO[]>(`/api/v1/workflow/quickComments/list`);
}

/** 根据 ID 查询 */
export function getQuickCommentByIdApi(id: string) {
  return requestClient.get<QuickCommentApi.QuickCommentVO>(`/api/v1/workflow/quickComments/${id}`);
}

/** 创建 */
export function createQuickCommentApi(data: QuickCommentApi.QuickCommentDTO) {
  return requestClient.post<string>(`/api/v1/workflow/quickComments`, data);
}

/** 更新 */
export function updateQuickCommentApi(data: QuickCommentApi.QuickCommentDTO) {
  return requestClient.put<boolean>(`/api/v1/workflow/quickComments`, data);
}

/** 删除 */
export function deleteQuickCommentApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/workflow/quickComments/${id}`);
}
