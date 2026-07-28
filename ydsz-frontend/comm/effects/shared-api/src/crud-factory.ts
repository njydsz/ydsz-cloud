/**
 * crud-factory 模块
 *
 * @path comm\effects\shared-api\src\crud-factory.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { RequestClient } from '@ydsz/request';
import type { CrudApi, PageQuery, PageResult, BaseResponse } from './types';

/**
 * CRUD 工厂配置选项 — 允许自定义 HTTP 方法和路径模式
 *
 * 大部分场景使用默认配置即可。仅当后端 API 路径非标准时需要覆盖。
 */
export interface CrudApiOptions {
  /** 分页查询路径后缀（默认 'page'） */
  pagePath?: string;
  /** 列表查询路径后缀（默认 'list'） */
  listPath?: string;
  /** 分页查询使用 GET（默认 true，后端标准模式）；设为 false 则使用 POST + body */
  pageUseGet?: boolean;
  /** 更新操作是否将 ID 放入 URL（默认 false，ID 在 body 中）；设为 true 则 PUT /basePath/{id} */
  updateWithIdInUrl?: boolean;
}

/**
 * 标准 CRUD API 工厂函数
 *
 * 各子应用可使用此函数快速创建标准 CRUD API 客户端，
 * 消除重复的 getById/page/list/create/update/delete 方法编写。
 *
 * 默认匹配 PMIS 后端标准 API 模式：
 * - GET  /basePath/page  — 分页查询（query params）
 * - GET  /basePath/list  — 列表查询
 * - GET  /basePath/{id}  — 按 ID 查询
 * - POST /basePath       — 创建
 * - PUT  /basePath       — 更新（ID 在 body 中）
 * - DELETE /basePath/{id} — 删除
 *
 * @example
 * ```ts
 * export const configApi = createCrudApi<ConfigVO, ConfigPageQuery, ConfigDTO>(
 *   requestClient,
 *   '/api/v1/config'
 * );
 *
 * // 使用
 * const result = await configApi.page({ pageNum: 1, pageSize: 10, configKey: 'test' });
 * const detail = await configApi.getById('123');
 * await configApi.create({ configKey: 'KEY', configValue: 'val' });
 * ```
 *
 * @param client 请求客户端实例
 * @param basePath API 基础路径（如 /api/v1/config）
 * @param options 可选配置（路径模式覆盖）
 * @returns 实现 CrudApi 接口的对象
 */
export function createCrudApi<T, Q = Record<string, never>, D = Partial<T>>(
  client: RequestClient,
  basePath: string,
  options?: CrudApiOptions,
): CrudApi<T, Q, D> {
  const {
    pagePath = 'page',
    listPath = 'list',
    pageUseGet = true,
    updateWithIdInUrl = false,
  } = options ?? {};

  return {
    getById: (id: string): Promise<BaseResponse<T>> =>
      client.get(`${basePath}/${id}`),

    page: (query: PageQuery & Q): Promise<BaseResponse<PageResult<T>>> =>
      pageUseGet
        ? client.get(`${basePath}/${pagePath}`, { params: query })
        : client.post(`${basePath}/${pagePath}`, query),

    list: (query?: Q): Promise<BaseResponse<T[]>> =>
      client.get(`${basePath}/${listPath}`, { params: query }),

    create: (data: D): Promise<BaseResponse<T>> =>
      client.post(basePath, data),

    update: (id: string, data: D): Promise<BaseResponse<T>> =>
      updateWithIdInUrl
        ? client.put(`${basePath}/${id}`, data)
        : client.put(basePath, data),

    remove: (id: string): Promise<BaseResponse<boolean>> =>
      client.delete(`${basePath}/${id}`),

    batchRemove: (ids: string[]): Promise<BaseResponse<boolean>> =>
      client.delete(`${basePath}/batch`, { data: ids }),
  };
}
