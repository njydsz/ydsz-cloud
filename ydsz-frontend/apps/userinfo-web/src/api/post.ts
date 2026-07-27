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

  export interface PostSaveDTO {
    id?: string;
    postCode: string;
    postName: string;
    sort?: number;
    status?: number;
    remark?: string;
  }
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
