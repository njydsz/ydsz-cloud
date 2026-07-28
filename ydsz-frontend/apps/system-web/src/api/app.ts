/**
 * 应用 API 模块（前端）
 * <p>封装应用（{@code ydsz_app}）CRUD 接口，对应后端 {@code /api/v1/system/app/*} 端点。
 * <p>系统中的应用是权限/菜单/路由的归属主体，支持多应用隔离。
 * <p>供「系统管理 → 应用管理」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

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
    appCode?: string;
    appName?: string;
    appSecret?: string;
    appType?: string;
    redirectUri?: string;
    status?: number;
    remark?: string;
  }
}

/** 分页查询app列表 */
export function getAppPageApi(params: AppApi.AppPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: AppApi.AppVO[];
  }>(`/api/v1/app/page`, { params });
}

/** 查询全部app列表 */
export function getAppListApi() {
  return requestClient.get<AppApi.AppVO[]>(`/api/v1/app/list`);
}

/** 根据 ID 查询app */
export function getAppByIdApi(id: string) {
  return requestClient.get<AppApi.AppVO>(`/api/v1/app/${id}`);
}

/** 创建app */
export function createAppApi(data: AppApi.AppDTO) {
  return requestClient.post<string>(`/api/v1/app`, data);
}

/** 更新app */
export function updateAppApi(data: AppApi.AppDTO) {
  return requestClient.put<boolean>(`/api/v1/app`, data);
}

/** 删除app */
export function deleteAppApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/app/${id}`);
}
