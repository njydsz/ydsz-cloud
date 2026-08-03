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
 * const { data, error } = await apiClient.GET('/api/system/users/{id}', {
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
  baseUrl: '/api/system',
});

/**
 * 便捷 API 调用示例
 *
 * <p>以下为常见 API 的封装示例，业务代码可直接使用或基于此扩展。
 */

/**
 * 获取用户列表
 * @param params - 查询参数（分页、筛选等）
 */
export async function getUsers(params?: {
  page?: number;
  pageSize?: number;
  keyword?: string;
}) {
  return apiClient.GET('/users', {
    params: { query: params },
  });
}

/**
 * 获取用户详情
 * @param id - 用户 ID
 */
export async function getUserById(id: string) {
  return apiClient.GET('/users/{id}', {
    params: { path: { id } },
  });
}

/**
 * 创建用户
 * @param data - 用户数据
 */
export async function createUser(data: {
  username: string;
  email: string;
  password: string;
}) {
  return apiClient.POST('/users', {
    body: data,
  });
}

/**
 * 更新用户
 * @param id - 用户 ID
 * @param data - 更新数据
 */
export async function updateUser(
  id: string,
  data: Partial<{
    username: string;
    email: string;
    password: string;
  }>,
) {
  return apiClient.PUT('/users/{id}', {
    params: { path: { id } },
    body: data,
  });
}

/**
 * 删除用户
 * @param id - 用户 ID
 */
export async function deleteUser(id: string) {
  return apiClient.DELETE('/users/{id}', {
    params: { path: { id } },
  });
}
