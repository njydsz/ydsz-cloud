/**
 * 共享 RequestClient 工厂 — 统一拦截器配置（successCode="A00000" + Bearer Token + refreshToken）
 *
 * 子应用调用 createSharedRequestClient() 即可获得与主应用一致的请求客户端。
 *
 * P0-F2: 支持 HttpOnly Cookie 模式。当 `VITE_APP_AUTH_TOKEN_STORAGE=httpOnlyCookie` 时：
 * - 启用 `withCredentials: true`，让浏览器自动携带 HttpOnly Secure Cookie
 * - 不再注入 `Authorization` 请求头（凭据由 Cookie 提供，前端无法读取）
 * - refreshToken 逻辑由后端 Cookie 续期接管，前端不再主动刷新
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
import { useAccessStore, useTokenStore } from '@ydsz/stores';

import { ElMessage } from 'element-plus';

import type { AuthApi } from './types';

/**
 * P0-F2: 认证令牌存储模式（构建期常量，与 auth.ts 保持一致）。
 *
 * @see comm/stores/src/modules/auth.ts 中的 isHttpOnlyCookieMode
 */
const isHttpOnlyCookieMode: boolean =
  import.meta.env.VITE_APP_AUTH_TOKEN_STORAGE === 'httpOnlyCookie';

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
    // P0-F2: HttpOnly Cookie 模式下启用跨域携带 Cookie，
    //        让浏览器自动发送 HttpOnly Secure Cookie 给后端
    withCredentials: isHttpOnlyCookieMode ? true : options?.withCredentials,
  });

  function formatToken(token: null | string) {
    return token ? `Bearer ${token}` : null;
  }

  // 请求头处理
  client.addRequestInterceptor({
    fulfilled: async (config) => {
      const tokenStore = useTokenStore();

      // P0-F2: HttpOnly Cookie 模式下不注入 Authorization 头，
      //        凭据由浏览器通过 HttpOnly Cookie 自动携带
      if (!isHttpOnlyCookieMode) {
        config.headers.Authorization = formatToken(tokenStore.accessToken);
      }
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
  // P0-F2: HttpOnly Cookie 模式下禁用前端 refreshToken 逻辑，
  //        401 时直接走 doReAuthenticate（后端通过 Cookie 续期或返回 401 触发重新登录）
  client.addResponseInterceptor(
    authenticateResponseInterceptor({
      client,
      doReAuthenticate: onReAuthenticate,
      doRefreshToken: onRefreshToken,
      enableRefreshToken: isHttpOnlyCookieMode ? false : preferences.app.enableRefreshToken,
      formatToken,
    }),
  );

  // 通用的错误处理
  // v3.4: 增强错误提示友好度
  //   - 401 已由 authenticateResponseInterceptor 处理，此处跳过避免重复弹窗
  //   - 5xx 错误附带 traceId 便于用户报障
  //   - 网络错误/超时给出可操作的中文提示
  client.addResponseInterceptor(
    errorMessageResponseInterceptor((msg: string, error) => {
      const status = error?.response?.status;
      // 401 由 authenticateResponseInterceptor 处理，跳过避免重复弹窗
      if (status === 401) return;

      const responseData = error?.response?.data ?? {};
      const serverMessage = responseData?.error ?? responseData?.message ?? '';
      const traceId = error?.config?.headers?.['X-Trace-Id'] as string | undefined;

      // 5xx 服务端错误：附带 traceId 便于报障
      if (status && status >= 500) {
        const tip = serverMessage || msg;
        const trace = traceId ? `\n追踪号: ${traceId}` : '';
        ElMessage.error({
          message: `${tip}${trace}`,
          duration: 6000,
        });
        return;
      }

      // 其他错误：优先服务端消息，其次本地化 msg
      ElMessage.error(serverMessage || msg);
    }),
  );

  return client;
}

/**
 * 创建共享的 baseRequestClient（无拦截器，用于 refresh/logout 等不需拦截的请求）
 *
 * P0-F2: HttpOnly Cookie 模式下同样启用 withCredentials，
 *        确保 logout 等请求能携带 Cookie 让后端清除凭据。
 */
export function createSharedBaseClient() {
  const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
  return new RequestClient({
    baseURL: apiURL,
    withCredentials: isHttpOnlyCookieMode ? true : undefined,
  });
}
