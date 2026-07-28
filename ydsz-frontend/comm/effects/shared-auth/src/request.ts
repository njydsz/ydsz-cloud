/**
 * 共享 RequestClient 工厂 — 统一拦截器配置（successCode="A00000" + Bearer Token + refreshToken）
 *
 * 子应用调用 createSharedRequestClient() 即可获得与主应用一致的请求客户端。
 */
import type { RequestClientOptions } from '@ydsz/request';

import { useAppConfig } from '@ydsz/hooks';
import { preferences } from '@ydsz/preferences';
import {
  authenticateResponseInterceptor,
  defaultResponseInterceptor,
  errorMessageResponseInterceptor,
  RequestClient,
} from '@ydsz/request';
import { useAccessStore } from '@ydsz/stores';

import { ElMessage } from 'element-plus';

import type { AuthApi } from './types';

/**
 * P1-6: 生成前端 TraceID（UUID v7 格式，时间排序友好）
 *
 * 用于前后端全链路追踪关联：前端在每个请求头中注入 X-Trace-Id，
 * 后端 SkyWalking/SentryLogbackLayout 会自动拾取该值作为 traceId。
 */
function generateTraceId(): string {
  // 优先使用 crypto.randomUUID()（现代浏览器原生支持）
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  // 降级方案：基于时间戳 + 随机数的简易 UUID
  const timestamp = Date.now().toString(36);
  const random = Math.random().toString(36).substring(2, 10);
  const random2 = Math.random().toString(36).substring(2, 10);
  return `${timestamp}-${random}-${random2}`;
}

/**
 * 创建与后端对齐的 RequestClient
 *
 * @param onReAuthenticate token 失效时的回调（通常由子应用传入 logout 逻辑）
 * @param onRefreshToken 刷新 token 的回调（通常由子应用传入 refreshToken 逻辑）
 * @param options 额外的 RequestClientOptions
 */
export function createSharedRequestClient(
  onReAuthenticate: () => Promise<void>,
  onRefreshToken: () => Promise<null | string>,
  options?: RequestClientOptions,
) {
  const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);

  const client = new RequestClient({
    ...options,
    baseURL: apiURL,
  });

  function formatToken(token: null | string) {
    return token ? `Bearer ${token}` : null;
  }

  // 请求头处理
  client.addRequestInterceptor({
    fulfilled: async (config) => {
      const accessStore = useAccessStore();

      config.headers.Authorization = formatToken(accessStore.accessToken);
      config.headers['Accept-Language'] = preferences.app.locale;
      // P1-6: 生成前端 TraceID，与后端日志/链路追踪关联
      if (!config.headers['X-Trace-Id']) {
        config.headers['X-Trace-Id'] = generateTraceId();
      }
      return config;
    },
  });

  // 处理返回的响应数据格式（对齐后端 BaseResponse: 业务响应码 code="A00000" 为成功，注意区分 HTTP 状态码 200）
  client.addResponseInterceptor(
    defaultResponseInterceptor({
      codeField: 'code',
      dataField: 'data',
      successCode: 'A00000',
    }),
  );

  // token过期的处理
  client.addResponseInterceptor(
    authenticateResponseInterceptor({
      client,
      doReAuthenticate: onReAuthenticate,
      doRefreshToken: onRefreshToken,
      enableRefreshToken: preferences.app.enableRefreshToken,
      formatToken,
    }),
  );

  // 通用的错误处理
  client.addResponseInterceptor(
    errorMessageResponseInterceptor((msg: string, error) => {
      const responseData = error?.response?.data ?? {};
      const errorMessage = responseData?.error ?? responseData?.message ?? '';
      ElMessage.error(errorMessage || msg);
    }),
  );

  return client;
}

/**
 * 创建共享的 baseRequestClient（无拦截器，用于 refresh/logout 等不需拦截的请求）
 */
export function createSharedBaseClient() {
  const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
  return new RequestClient({ baseURL: apiURL });
}
