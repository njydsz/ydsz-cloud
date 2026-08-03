/**
 * 文件评论 API 模块（前端）
 * <p>封装文件评论接口，对应后端 {@code /api/v1/nextwiki/comment/*} 端点。
 * <p>支持楼中楼回复、@提及、Markdown 格式、表情点赞。
 * <p>供「知识库 → 文档评论」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace CommentApi {
  /** 文档评论视图对象 */
  export interface CommentVO {
    id: string;
    fileId: string;
    fileName: string;
    userId: string;
    content: string;
    createTime: string;
  }

  /** 评论分页查询参数 */
  export interface CommentPageQuery {
    pageNum?: number;
    pageSize?: number;
    fileId?: string;
  }

  /** 评论创建/更新请求参数 */
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
