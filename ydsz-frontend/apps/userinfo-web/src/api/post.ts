/**
 * 岗位 API 模块（前端）
 *
 * 封装岗位（{@code ydsz_post}）CRUD 接口，对应后端 {@code /api/v1/post/*} 端点。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace PostApi {
  export interface PostVO {
    id: string;
    postCode: string;
    postName: string;
    sort?: number;
    status: number;
    remark?: string;
    createTime?: string;
  }

  export interface PostPageQuery {
    pageNum?: number;
    pageSize?: number;
    postName?: string;
    postCode?: string;
    status?: number;
  }

  export interface PostSaveDTO {
    id?: string;
    postCode: string;
    postName: string;
    sort?: number;
    status?: number;
    remark?: string;
  }
}

/** 分页查询岗位列表 */
export function getPostPageApi(params: PostApi.PostPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: PostApi.PostVO[];
  }>('/api/v1/post/page', { params });
}

/** 查询全部岗位列表 */
export function getPostListApi() {
  return requestClient.get<PostApi.PostVO[]>('/api/v1/post/list');
}

/** 根据 ID 查询岗位 */
export function getPostByIdApi(id: string) {
  return requestClient.get<PostApi.PostVO>(`/api/v1/post/${id}`);
}

/** 创建岗位 */
export function createPostApi(data: PostApi.PostSaveDTO) {
  return requestClient.post<string>('/api/v1/post', data);
}

/** 更新岗位 */
export function updatePostApi(data: PostApi.PostSaveDTO) {
  return requestClient.put<boolean>('/api/v1/post', data);
}

/** 删除岗位 */
export function deletePostApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/post/${id}`);
}
