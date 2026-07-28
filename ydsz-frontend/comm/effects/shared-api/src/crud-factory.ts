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
 * 标准 CRUD API 工厂函数
 *
 * 各子应用可使用此函数快速创建标准 CRUD API 客户端，
 * 消除重复的 getById/page/list/create/update/delete 方法编写。
 *
 * @example
 * ```ts
 * export const projectApi = createCrudApi<ProjectInitiation, ProjectQuery, ProjectDTO>(
 *   requestClient,
 *   '/api/v1/project/initiation'
 * );
 *
 * // 使用
 * const result = await projectApi.page({ pageNum: 1, pageSize: 10, projectName: 'test' });
 * const detail = await projectApi.getById('123');
 * await projectApi.create({ projectCode: 'P001', projectName: 'Test' });
 * ```
 *
 * @param client 请求客户端实例
 * @param basePath API 基础路径（如 /api/v1/project/initiation）
 * @returns 实现 CrudApi 接口的对象
 */
export function createCrudApi<T, Q = Record<string, never>, D = Partial<T>>(
  client: RequestClient,
  basePath: string,
): CrudApi<T, Q, D> {
  return {
    getById: (id: string): Promise<BaseResponse<T>> =>
      client.get(`${basePath}/${id}`),

    page: (query: PageQuery & Q): Promise<BaseResponse<PageResult<T>>> =>
      client.post(`${basePath}/page`, query),

    list: (query?: Q): Promise<BaseResponse<T[]>> =>
      client.get(basePath, { params: query }),

    create: (data: D): Promise<BaseResponse<T>> =>
      client.post(basePath, data),

    update: (id: string, data: D): Promise<BaseResponse<T>> =>
      client.put(`${basePath}/${id}`, data),

    remove: (id: string): Promise<BaseResponse<boolean>> =>
      client.delete(`${basePath}/${id}`),

    batchRemove: (ids: string[]): Promise<BaseResponse<boolean>> =>
      client.delete(`${basePath}/batch`, { data: ids }),
  };
}
