/**
 * 应用 API 模块（前端）
 *
 * 封装应用（{@code ydsz_app}）CRUD 接口，对应后端 {@code /api/v1/app/*} 端点。
 * 使用 @ydsz/shared-api 的 createCrudApi 工厂消除重复 CRUD 代码。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';
import { createCrudApi } from '@ydsz/shared-api';

export namespace AppApi {
  export interface AppVO {
    id: string;
    appCode: string;
    appName: string;
    appSecret: string;
    appType: string;
    redirectUri: string;
    status: number;
    remark: string;
    createTime: string;
  }

  export interface AppPageQuery {
    pageNum?: number;
    pageSize?: number;
    appName?: string;
    status?: string;
  }

  export interface AppDTO {
    id?: string;
    appCode?: string;
    appName?: string;
    appSecret?: string;
    appType?: string;
    redirectUri?: string;
    status?: number;
    remark?: string;
  }
}

/** 应用 CRUD API（由 createCrudApi 工厂创建） */
export const appApi = createCrudApi<
  AppApi.AppVO,
  AppApi.AppPageQuery,
  AppApi.AppDTO
>(requestClient, '/api/v1/app');

/**
 * 分页查询应用
 * @deprecated 使用 appApi.page() 替代
 */
export function getAppPageApi(params: AppApi.AppPageQuery) {
  return appApi.page(params as any);
}

/**
 * 查询全部应用
 * @deprecated 使用 appApi.list() 替代
 */
export function getAppListApi() {
  return appApi.list();
}

/**
 * 根据 ID 查询应用
 * @deprecated 使用 appApi.getById() 替代
 */
export function getAppByIdApi(id: string) {
  return appApi.getById(id);
}

/**
 * 创建应用
 * @deprecated 使用 appApi.create() 替代
 */
export function createAppApi(data: AppApi.AppDTO) {
  return appApi.create(data);
}

/**
 * 更新应用
 * @deprecated 使用 appApi.update() 替代
 */
export function updateAppApi(data: AppApi.AppDTO) {
  return appApi.update(data.id ?? '', data);
}

/**
 * 删除应用
 * @deprecated 使用 appApi.remove() 替代
 */
export function deleteAppApi(id: string) {
  return appApi.remove(id);
}
