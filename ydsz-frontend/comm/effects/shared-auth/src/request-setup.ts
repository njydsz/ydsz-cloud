/**
 * request-setup — 延迟初始化的 requestClient/baseRequestClient
 *
 * 子应用在 main.ts 的 mount() 阶段调用 initSharedRequest() 注入回调后，
 * 本模块导出的 requestClient / baseRequestClient 才可安全使用。
 */
import type { RequestClient } from '@ydsz/request';

import { createSharedBaseClient, createSharedRequestClient } from './request';

let _requestClient: null | RequestClient = null;
let _baseRequestClient: null | RequestClient = null;

/**
 * 初始化共享请求客户端（由子应用 main.ts 调用）
 */
export function initSharedRequest(
  onReAuthenticate: () => Promise<void>,
  onRefreshToken: () => Promise<null | string>,
) {
  if (!_requestClient) {
    _requestClient = createSharedRequestClient(
      onReAuthenticate,
      onRefreshToken,
      { responseReturn: 'data' },
    );
  }
  if (!_baseRequestClient) {
    _baseRequestClient = createSharedBaseClient();
  }
}

export const requestClient: RequestClient = new Proxy(
  {} as RequestClient,
  {
    get(_target, prop) {
      if (!_requestClient) {
        throw new Error(
          '[shared-auth] requestClient not initialized. Call initSharedRequest() first.',
        );
      }
      return Reflect.get(_requestClient, prop);
    },
  },
);

export const baseRequestClient: RequestClient = new Proxy(
  {} as RequestClient,
  {
    get(_target, prop) {
      if (!_baseRequestClient) {
        throw new Error(
          '[shared-auth] baseRequestClient not initialized. Call initSharedRequest() first.',
        );
      }
      return Reflect.get(_baseRequestClient, prop);
    },
  },
);
