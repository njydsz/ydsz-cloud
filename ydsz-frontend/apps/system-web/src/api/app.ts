/**
 * 应用 API 模块（前端）
 *
 * 封装应用（{@code ydsz_app}）CRUD 接口，对应后端 {@code /api/v1/app/*} 端点。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace AppApi {
  /** 应用视图对象 */
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

  /** 应用分页查询参数 */
  export interface AppPageQuery {
    pageNum?: number;
    pageSize?: number;
    appName?: string;
    status?: string;
  }

  /** 应用创建/更新请求参数 */
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

/** 分页查询应用 */
export function getAppPageApi(params: AppApi.AppPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: AppApi.AppVO[];
  }>('/api/v1/app/page', { params });
}

/** 查询全部应用 */
export function getAppListApi() {
  return requestClient.get<AppApi.AppVO[]>('/api/v1/app/list');
}

/** 根据 ID 查询应用 */
export function getAppByIdApi(id: string) {
  return requestClient.get<AppApi.AppVO>(`/api/v1/app/${id}`);
}

/** 创建应用 */
export function createAppApi(data: AppApi.AppDTO) {
  return requestClient.post<string>('/api/v1/app', data);
}

/** 更新应用 */
export function updateAppApi(data: AppApi.AppDTO) {
  return requestClient.put<boolean>('/api/v1/app', data);
}

/** 删除应用 */
export function deleteAppApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/app/${id}`);
}
