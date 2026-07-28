/**
 * 岗位 API 模块（前端）
 *
 * 封装岗位（{@code ydsz_post}）CRUD 接口，对应后端 {@code /api/v1/post/*} 端点。
 * 使用 @ydsz/shared-api 的 createCrudApi 工厂消除重复 CRUD 代码。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';
import { createCrudApi } from '@ydsz/shared-api';

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

/** 岗位 CRUD API（由 createCrudApi 工厂创建） */
export const postApi = createCrudApi<
  PostApi.PostVO,
  PostApi.PostPageQuery,
  PostApi.PostSaveDTO
>(requestClient, '/api/v1/post');

/** 查询全部岗位列表 */
export function getPostListApi() {
  return postApi.list();
}

/** 根据 ID 查询岗位 */
export function getPostByIdApi(id: string) {
  return postApi.getById(id);
}

/** 创建岗位 */
export function createPostApi(data: PostApi.PostSaveDTO) {
  return postApi.create(data);
}

/** 更新岗位 */
export function updatePostApi(data: PostApi.PostSaveDTO) {
  return postApi.update(data.id ?? '', data);
}

/** 删除岗位 */
export function deletePostApi(id: string) {
  return postApi.remove(id);
}
