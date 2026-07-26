/**
 * @file CRUD API 工厂函数
 * @description 消除 page/get/create/update/delete 重复模式，一个 createCrudApi 调用即可生成完整 CRUD API。
 * @module utils/crudApi
 */
import { request, type PageResult } from '@/utils/request'

export interface CrudApiConfig {
  /** API base path, e.g. '/api/project/contract' */
  basePath: string
  /** Custom page endpoint path (defaults to `${basePath}/page`) */
  pagePath?: string
}

export interface CrudApi<T, Q extends Record<string, unknown> = Record<string, unknown>> {
  page: (page: number, size: number, params?: Q) => Promise<PageResult<T>>
  get: (id: string | number) => Promise<T>
  create: (data: Partial<T>) => Promise<string>
  update: (data: Partial<T>) => Promise<void>
  remove: (id: string | number) => Promise<void>
}

export function createCrudApi<T, Q extends Record<string, unknown> = Record<string, unknown>>(
  config: CrudApiConfig | string,
): CrudApi<T, Q> {
  const basePath = typeof config === 'string' ? config : config.basePath
  const pagePath =
    typeof config === 'string'
      ? `${config}/page`
      : config.pagePath ?? `${config.basePath}/page`

  return {
    page: (page, size, params) =>
      request<PageResult<T>>({
        url: pagePath,
        method: 'GET',
        params: { page, size, ...params },
      }) as unknown as Promise<PageResult<T>>,
    get: (id) =>
      request<T>({ url: `${basePath}/${id}`, method: 'GET' }) as unknown as Promise<T>,
    create: (data) =>
      request<string>({ url: basePath, method: 'POST', data }) as unknown as Promise<string>,
    update: (data) =>
      request<void>({ url: basePath, method: 'PUT', data }) as unknown as Promise<void>,
    remove: (id) =>
      request<void>({ url: `${basePath}/${id}`, method: 'DELETE' }) as unknown as Promise<void>,
  }
}
