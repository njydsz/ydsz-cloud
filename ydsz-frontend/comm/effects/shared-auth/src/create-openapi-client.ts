/**
 * OpenAPI SDK 客户端工厂函数
 *
 * <p>基于 openapi-fetch 创建类型安全的 API 客户端，与现有 requestClient 集成。
 * 各子应用使用自身的 schema 类型调用此工厂创建专属客户端。
 *
 * @path comm/effects/shared-auth/src/create-openapi-client.ts
 * @author ydsz-team
 * @since 1.0.0
 */

import type { ClientOptions } from 'openapi-fetch';
import createClient from 'openapi-fetch';

import { requestClient } from './request-setup';

/**
 * 创建 OpenAPI SDK 客户端
 *
 * <p>封装 openapi-fetch，复用现有 requestClient 的拦截器链（Token 注入、TraceId、错误处理），
 * 提供类型安全的 API 调用（基于 openapi-typescript 生成的 schema.d.ts）。
 *
 * @example
 * ```ts
 * // 在 system-web 中使用
 * import { createOpenApiClient } from '@ydsz/shared-auth';
 * import type { paths } from '#/api/sdk/schema';
 *
 * const apiClient = createOpenApiClient<paths>({ baseUrl: '/api/system' });
 *
 * // 类型安全的 API 调用
 * const { data, error } = await apiClient.GET('/users/{id}', {
 *   params: { path: { id: '123' } },
 * });
 * ```
 *
 * @param options - openapi-fetch 配置选项（baseUrl 等）
 * @returns 类型安全的 OpenAPI 客户端
 */
export function createOpenApiClient<T extends Record<string, any>>(
  options?: ClientOptions,
) {
  return createClient<T>({
    // 复用 requestClient 的 axios 实例作为底层传输
    fetch: async (url: string | URL | Request, init?: RequestInit) => {
      const urlStr = typeof url === 'string' ? url : url instanceof URL ? url.toString() : url.url;
      const method = init?.method || 'GET';

      // 解析 headers
      const headers: Record<string, string> = {};
      if (init?.headers) {
        if (init.headers instanceof Headers) {
          init.headers.forEach((v, k) => { headers[k] = v; });
        } else if (Array.isArray(init.headers)) {
          for (const [k, v] of init.headers) { headers[k] = v; }
        } else {
          Object.assign(headers, init.headers);
        }
      }

      // 解析 body
      let data: any;
      if (init?.body) {
        try {
          data = JSON.parse(init.body as string);
        } catch {
          data = init.body;
        }
      }

      // 使用 requestClient 发起请求（复用拦截器链）
      const response = await requestClient.request({
        url: urlStr,
        method: method as any,
        headers,
        data,
      });

      // 转换为标准 Response 对象（openapi-fetch 期望）
      return new Response(JSON.stringify(response.data), {
        status: response.status,
        statusText: response.statusText,
      });
    },
    ...options,
  });
}
