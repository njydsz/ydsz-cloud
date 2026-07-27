import type { AxiosInstance, AxiosResponse } from 'axios';

import type {
  RequestInterceptorConfig,
  ResponseInterceptorConfig,
} from '../types';

const defaultRequestInterceptorConfig: RequestInterceptorConfig = {
  fulfilled: (response) => response,
  rejected: (error) => Promise.reject(error),
};

const defaultResponseInterceptorConfig: ResponseInterceptorConfig = {
  fulfilled: (response: AxiosResponse) => response,
  rejected: (error) => Promise.reject(error),
};

class InterceptorManager {
  private axiosInstance: AxiosInstance;

  constructor(instance: AxiosInstance) {
    this.axiosInstance = instance;
  }

  /**
   * 添加请求拦截器
   * @returns 拦截器 ID，可用于后续移除
   */
  addRequestInterceptor({
    fulfilled,
    rejected,
  }: RequestInterceptorConfig = defaultRequestInterceptorConfig): number {
    return this.axiosInstance.interceptors.request.use(fulfilled, rejected);
  }

  /**
   * 添加响应拦截器
   * @returns 拦截器 ID，可用于后续移除
   */
  addResponseInterceptor<T = any>({
    fulfilled,
    rejected,
  }: ResponseInterceptorConfig<T> = defaultResponseInterceptorConfig): number {
    return this.axiosInstance.interceptors.response.use(fulfilled, rejected);
  }

  /**
   * 移除请求拦截器
   * @param id 拦截器 ID
   */
  removeRequestInterceptor(id: number): void {
    this.axiosInstance.interceptors.request.eject(id);
  }

  /**
   * 移除响应拦截器
   * @param id 拦截器 ID
   */
  removeResponseInterceptor(id: number): void {
    this.axiosInstance.interceptors.response.eject(id);
  }
}

export { InterceptorManager };
