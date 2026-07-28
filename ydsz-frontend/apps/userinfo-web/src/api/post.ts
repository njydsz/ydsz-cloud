/**
 * 岗位 API 模块（前端）
 * <p>封装岗位（{@code ydsz_post}）CRUD 接口，对应后端 {@code /api/v1/userinfo/post/*} 端点。
 * <p>岗位是组织内的职位定义，与职级/计费卡关联。
 * <p>供「组织架构 → 岗位管理」使用。
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
