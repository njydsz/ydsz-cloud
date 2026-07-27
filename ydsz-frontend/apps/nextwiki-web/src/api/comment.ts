import { requestClient } from '#/api/request';

export namespace CommentApi {
  export interface CommentVO {
    id: string;
    fileId: string;
    fileName: string;
    userId: string;
    content: string;
    createTime: string;
  }

  export interface CommentPageQuery {
    pageNum?: number;
    pageSize?: number;
    fileId?: string;
  }

  export interface CommentDTO {
    fileId?: string;
    content?: string;
  }
}

/** 分页查询 */
export function getCommentPageApi(params: CommentApi.CommentPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: CommentApi.CommentVO[];
  }>(`/api/v1/nextwiki/comments/page`, { params });
}

/** 查询全部列表 */
export function getCommentListApi() {
  return requestClient.get<CommentApi.CommentVO[]>(`/api/v1/nextwiki/comments/list`);
}

/** 根据 ID 查询 */
export function getCommentByIdApi(id: string) {
  return requestClient.get<CommentApi.CommentVO>(`/api/v1/nextwiki/comments/${id}`);
}

/** 创建 */
export function createCommentApi(data: CommentApi.CommentDTO) {
  return requestClient.post<string>(`/api/v1/nextwiki/comments`, data);
}

/** 更新 */
export function updateCommentApi(data: CommentApi.CommentDTO) {
  return requestClient.put<boolean>(`/api/v1/nextwiki/comments`, data);
}

/** 删除 */
export function deleteCommentApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/nextwiki/comments/${id}`);
}
