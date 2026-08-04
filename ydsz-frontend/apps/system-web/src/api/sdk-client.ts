/**
 * System Web OpenAPI SDK 客户端
 *
 * <p>基于 openapi-fetch 创建的类型安全 API 客户端，
 * 与现有 requestClient 集成，复用 Token 注入、TraceId、错误处理等拦截器。
 *
 * <p>使用方式：
 * ```ts
 * import { apiClient } from '#/api/sdk-client';
 *
 * // 类型安全的 API 调用
 * const { data, error } = await apiClient.GET('/api/v1/dict/type/{id}', {
 *   params: { path: { id: '123' } },
 * });
 * ```
 *
 * @author ydsz-team
 * @since 1.0.0
 */

import { createOpenApiClient } from '@ydsz/shared-auth';
import type { paths } from './sdk/schema';

/**
 * System Web 类型安全 API 客户端
 *
 * <p>基于生成的 schema.d.ts 提供完整的类型检查和自动补全。
 * 所有 API 路径、参数、响应类型均与后端 OpenAPI 规范对齐。
 */
export const apiClient = createOpenApiClient<paths>({
  baseUrl: '',
});

/**
 * 便捷 API 调用示例
 *
 * <p>以下为常见 API 的封装示例，业务代码可直接使用或基于此扩展。
 */

/**
 * 获取字典类型分页
 * @param params - 查询参数（分页、筛选等）
 */
export async function getDictTypePage(params?: {
  pageNum?: number;
  pageSize?: number;
  typeName?: string;
  typeCode?: string;
}) {
  return apiClient.GET('/api/v1/dict/type/page', {
    params: { query: params },
  });
}

/**
 * 获取字典类型详情
 * @param id - 字典类型 ID
 */
export async function getDictTypeById(id: string) {
  return apiClient.GET('/api/v1/dict/type/{id}', {
    params: { path: { id } },
  });
}

/**
 * 按类型编码查询字典项
 * @param typeCode - 字典类型编码
 */
export async function getDictItemsByType(typeCode: string) {
  return apiClient.GET('/api/v1/dict/item/type/{typeCode}', {
    params: { path: { typeCode } },
  });
}
