/**
 * request-client 模块
 *
 * @path comm\effects\request\src\request-client\request-client.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { AxiosInstance, AxiosResponse } from 'axios';

import type { RequestClientConfig, RequestClientOptions } from './types';

import { bindMethods, isString, merge } from '@ydsz/utils';

import axios from 'axios';
import qs from 'qs';

import { BusinessError } from './business-error';
import { FileDownloader } from './modules/downloader';
import { InterceptorManager } from './modules/interceptor';
import { FileUploader } from './modules/uploader';

function getParamsSerializer(
  paramsSerializer: RequestClientOptions['paramsSerializer'],
) {
  if (isString(paramsSerializer)) {
    switch (paramsSerializer) {
      case 'brackets': {
        return (params: any) =>
          qs.stringify(params, { arrayFormat: 'brackets' });
      }
      case 'comma': {
        return (params: any) => qs.stringify(params, { arrayFormat: 'comma' });
      }
      case 'indices': {
        return (params: any) =>
          qs.stringify(params, { arrayFormat: 'indices' });
      }
      case 'repeat': {
        return (params: any) => qs.stringify(params, { arrayFormat: 'repeat' });
      }
    }
  }
  return paramsSerializer;
}

class RequestClient {
  public addRequestInterceptor: InterceptorManager['addRequestInterceptor'];

  public addResponseInterceptor: InterceptorManager['addResponseInterceptor'];
  public download: FileDownloader['download'];

  /** 是否正在刷新token */
  public isRefreshing = false;
  /** 刷新token队列，包含 resolve 和 reject 回调 */
  public refreshTokenQueue: {
    reject: (error: unknown) => void;
    resolve: (token: string) => void;
  }[] = [];
  /** 移除请求拦截器 */
  public removeRequestInterceptor: InterceptorManager['removeRequestInterceptor'];
  /** 移除响应拦截器 */
  public removeResponseInterceptor: InterceptorManager['removeResponseInterceptor'];
  public upload: FileUploader['upload'];

  /**
   * 按 URL 索引的在途请求 AbortController 集合。
   *
   * v3.1 修复：此前 map 仅声明未写入，cancelRequest/cancelAllRequests 为死代码。
   * 现在每个 request() 调用创建独立 controller 并注入 axios signal，
   * 同一 URL 的并发请求各自独立，均可被取消。
   */
  private abortControllerMap: Map<string, Set<AbortController>> = new Map();

  private readonly instance: AxiosInstance;

  /** 重试配置 */
  private retryConfig: {
    /** 重试条件判断函数 */
    retryCondition?: (error: unknown) => boolean;
    /** 重试延迟（毫秒） */
    retryDelay?: number;
    /** 最大重试次数 */
    retryCount?: number;
    /** 退避策略：'fixed' | 'exponential' */
    retryBackoff?: 'exponential' | 'fixed';
    /** 随机抖动因子（0~1），仅 exponential 模式下生效 */
    retryJitter?: number;
  } = {};

  constructor(options: RequestClientOptions = {}) {
    const defaultConfig: RequestClientOptions = {
      headers: {
        'Content-Type': 'application/json;charset=utf-8',
      },
      responseReturn: 'raw',
      timeout: 10_000,
    };
    const { retry, ...axiosConfig } = options;
    const requestConfig = merge(axiosConfig, defaultConfig);
    requestConfig.paramsSerializer = getParamsSerializer(
      requestConfig.paramsSerializer,
    );
    this.instance = axios.create(requestConfig);

    if (retry) {
      this.retryConfig = retry;
    }

    bindMethods(this);

    const interceptorManager = new InterceptorManager(this.instance);
    this.addRequestInterceptor =
      interceptorManager.addRequestInterceptor.bind(interceptorManager);
    this.addResponseInterceptor =
      interceptorManager.addResponseInterceptor.bind(interceptorManager);
    this.removeRequestInterceptor =
      interceptorManager.removeRequestInterceptor.bind(interceptorManager);
    this.removeResponseInterceptor =
      interceptorManager.removeResponseInterceptor.bind(interceptorManager);

    const fileUploader = new FileUploader(this);
    this.upload = fileUploader.upload.bind(fileUploader);
    const fileDownloader = new FileDownloader(this);
    this.download = fileDownloader.download.bind(fileDownloader);
  }

  /**
   * DELETE请求方法
   */
  public delete<T = any>(
    url: string,
    config?: RequestClientConfig,
  ): Promise<T> {
    return this.request<T>(url, { ...config, method: 'DELETE' });
  }

  /**
   * GET请求方法
   */
  public get<T = any>(url: string, config?: RequestClientConfig): Promise<T> {
    return this.request<T>(url, { ...config, method: 'GET' });
  }

  /**
   * POST请求方法
   */
  public post<T = any>(
    url: string,
    data?: any,
    config?: RequestClientConfig,
  ): Promise<T> {
    return this.request<T>(url, { ...config, data, method: 'POST' });
  }

  /**
   * PUT请求方法
   */
  public put<T = any>(
    url: string,
    data?: any,
    config?: RequestClientConfig,
  ): Promise<T> {
    return this.request<T>(url, { ...config, data, method: 'PUT' });
  }

  /**
   * 通用的请求方法
   *
   * v3.1 修复：为每次请求创建 AbortController 并注入 axios signal，
   * 使 cancelRequest/cancelAllRequests 真正生效；请求完成（成功/失败）后从 map 清理。
   * 若调用方在 config 中自带 signal，则不创建内部 controller，由调用方自行管理。
   */
  public async request<T>(
    url: string,
    config: RequestClientConfig,
  ): Promise<T> {
    const maxRetries = this.retryConfig.retryCount ?? 0;
    const baseDelay = this.retryConfig.retryDelay ?? 1000;
    const backoff = this.retryConfig.retryBackoff ?? 'fixed';
    const jitter = this.retryConfig.retryJitter ?? 0;
    const retryCondition = this.retryConfig.retryCondition ?? (() => false);

    // 为本次请求创建内部 controller（调用方未提供 signal 时）
    const ownController =
      config.signal instanceof AbortSignal ? null : new AbortController();
    if (ownController) {
      const set = this.abortControllerMap.get(url);
      if (set) {
        set.add(ownController);
      } else {
        this.abortControllerMap.set(url, new Set([ownController]));
      }
    }
    const signal = config.signal instanceof AbortSignal ? config.signal : ownController!.signal;

    const cleanup = (): void => {
      if (ownController) {
        const set = this.abortControllerMap.get(url);
        if (set) {
          set.delete(ownController);
          if (set.size === 0) this.abortControllerMap.delete(url);
        }
      }
    };

    let lastError: unknown;

    try {
      for (let attempt = 0; attempt <= maxRetries; attempt++) {
        try {
          const response: AxiosResponse<T> = await this.instance({
            url,
            ...config,
            signal,
            ...(config.paramsSerializer
              ? { paramsSerializer: getParamsSerializer(config.paramsSerializer) }
              : {}),
          });

          if (config.responseReturn === 'raw') {
            return response as T;
          }

          return response as unknown as T;
        } catch (error: unknown) {
          lastError = error;

          // 主动取消不重试
          if (signal.aborted) {
            throw error;
          }

          if (attempt < maxRetries && retryCondition(error)) {
            // 指数退避 + 随机抖动，避免惊群效应
            const delay =
              backoff === 'exponential'
                ? baseDelay * 2 ** attempt + Math.random() * jitter
                : baseDelay;
            await new Promise((resolve) => setTimeout(resolve, delay));
            continue;
          }

          if (axios.isAxiosError(error)) {
            const axiosError = error;
            if (axiosError.response) {
              throw new BusinessError(
                axiosError.response.statusText || axiosError.message,
                {
                  data: axiosError.response.data,
                  statusCode: axiosError.response.status,
                },
              );
            }
            if (axiosError.code === 'ECONNABORTED' || axiosError.code === 'ETIMEDOUT') {
              throw new BusinessError('请求超时', { statusCode: 408 });
            }
            throw new BusinessError(axiosError.message || '网络请求失败');
          }
          throw error;
        }
      }
    } finally {
      cleanup();
    }

    throw lastError;
  }

  /**
   * 取消指定 URL 的全部在途请求
   * @param url 请求 URL
   */
  public cancelRequest(url: string): void {
    const set = this.abortControllerMap.get(url);
    if (!set) return;
    for (const controller of set) {
      controller.abort();
    }
    this.abortControllerMap.delete(url);
  }

  /**
   * 取消所有进行中的请求
   */
  public cancelAllRequests(): void {
    for (const set of this.abortControllerMap.values()) {
      for (const controller of set) {
        controller.abort();
      }
    }
    this.abortControllerMap.clear();
  }
}

export { RequestClient };
