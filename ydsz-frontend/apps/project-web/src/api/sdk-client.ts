import { createOpenApiClient } from '@ydsz/shared-auth';
import type { paths } from './sdk/schema';

/**
 * Project Web OpenAPI SDK 客户端
 *
 * 类型安全调用，复用 requestClient 的拦截器链。
 * schema.d.ts 由 gen-api.mjs 自动生成，请勿手动编辑。
 *
 * @path apps/project-web/src/api/sdk-client.ts
 * @since 1.0.0
 */
export const apiClient = createOpenApiClient<paths>({ baseUrl: '/api/project' });
