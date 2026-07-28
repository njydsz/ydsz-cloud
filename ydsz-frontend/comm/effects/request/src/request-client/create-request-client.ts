/**
 * create-request-client 模块
 *
 * @path comm\effects\request\src\request-client\create-request-client.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { RequestClient, RequestClientOptions } from './request-client';

/**
 * 创建请求客户端的配置选项
 */
export interface CreateRequestClientOptions {
  /** API 基础路径 */
  baseURL: string;
  /** 额外的 RequestClient 配置 */
  options?: RequestClientOptions;
}

/**
 * 创建请求客户端工厂函数
 * @description 提取四套应用共享的请求客户端创建逻辑，各应用在此基础上自行注册拦截器
 * @param config 创建配置
 * @returns 请求客户端实例和基础请求客户端实例
 */
export function createRequestClient(
  config: CreateRequestClientOptions,
): {
  baseRequestClient: RequestClient;
  requestClient: RequestClient;
} {
  const { baseURL, options = {} } = config;

  const client = new RequestClient({
    ...options,
    baseURL,
  });

  const baseRequestClient = new RequestClient({
    ...options,
    baseURL,
  });

  return {
    baseRequestClient,
    requestClient: client,
  };
}
