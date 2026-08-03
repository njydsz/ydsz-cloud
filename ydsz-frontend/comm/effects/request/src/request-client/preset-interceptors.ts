/**
 * preset-interceptors 模块
 *
 * @path comm\effects\request\src\request-client\preset-interceptors.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { RequestClient } from './request-client';
import type { MakeErrorMessageFn, ResponseInterceptorConfig } from './types';

import { $t } from '@ydsz/locales';
import { isFunction } from '@ydsz/utils';

import axios from 'axios';

import { BusinessError } from './business-error';

/** 默认响应拦截器：按 codeField/successCode 判定业务成功，剥离 dataField 数据或抛 BusinessError */
export const defaultResponseInterceptor = ({
  codeField = 'code',
  dataField = 'data',
  successCode = 0,
}: {
  /** 响应数据中代表访问结果的字段名 */
  codeField: string;
  /** 响应数据中装载实际数据的字段名，或者提供一个函数从响应数据中解析需要返回的数据 */
  dataField: ((response: any) => any) | string;
  /** 当codeField所指定的字段值与successCode相同时，代表接口访问成功。如果提供一个函数，则返回true代表接口访问成功 */
  successCode: ((code: any) => boolean) | number | string;
}): ResponseInterceptorConfig => {
  return {
    fulfilled: (response) => {
      const { config, data: responseData, status } = response;

      if (config.responseReturn === 'raw') {
        return response;
      }

      if (status >= 200 && status < 400) {
        if (config.responseReturn === 'body') {
          return responseData;
        } else if (
          isFunction(successCode)
            ? successCode(responseData[codeField])
            : responseData[codeField] === successCode
        ) {
          return isFunction(dataField)
            ? dataField(responseData)
            : responseData[dataField];
        }
      }
      throw new BusinessError(
        responseData?.message || '业务请求失败',
        {
          code: responseData?.[codeField],
          data: responseData,
          statusCode: status,
        },
      );
    },
  };
};

/**
 * 创建「401 自动刷新 token 并重放请求」的响应拦截器。
 *
 * @remarks
 * 处理流程：
 * 1. 非 401 错误原样抛出，不做任何干预；
 * 2. 未开启 refreshToken，或该请求已是重试请求（`config.__isRetryRequest`）时，
 *    直接走重新登录并抛出原错误——`__isRetryRequest` 是防止「刷新后仍 401」造成无限重试的关键开关；
 * 3. 若已有刷新在途（`client.isRefreshing`），当前请求挂入 `refreshTokenQueue` 排队，
 *    刷新成功后用新 token 重放，保证并发 401 只触发**一次**刷新；
 * 4. 否则由本请求发起刷新，成功后先唤醒队列再重放自身。
 *
 * 失败与副作用：刷新失败时会 reject 队列中所有等待请求、清空队列、执行 `doReAuthenticate`
 * （通常是跳转登录页）并抛出刷新错误；无论成败 `finally` 中都会复位 `isRefreshing`，
 * 避免刷新标记泄漏导致后续请求永久排队。
 *
 * 注意：拦截器直接读写 `client.isRefreshing` 与 `client.refreshTokenQueue`，
 * 与传入的 `client` 强耦合，请勿在多个 client 之间共享同一个拦截器实例。
 *
 * @param options - 拦截器依赖项
 * @param options.client - 该拦截器所属的请求客户端，用于共享刷新状态与重放请求
 * @param options.doReAuthenticate - 重新认证回调，通常清理登录态并跳转登录页
 * @param options.doRefreshToken - 刷新 token 回调，需返回新的 token 字符串
 * @param options.enableRefreshToken - 是否启用无感刷新；为 false 时 401 直接走重新认证
 * @param options.formatToken - 把 token 格式化为 Authorization 头的值（如加 `Bearer ` 前缀）
 * @returns 可注册到请求客户端的响应拦截器配置（仅含 `rejected` 分支）
 */
export const authenticateResponseInterceptor = ({
  client,
  doReAuthenticate,
  doRefreshToken,
  enableRefreshToken,
  formatToken,
}: {
  client: RequestClient;
  doReAuthenticate: () => Promise<void>;
  doRefreshToken: () => Promise<string>;
  enableRefreshToken: boolean;
  formatToken: (token: string) => null | string;
}): ResponseInterceptorConfig => {
  return {
    rejected: async (error) => {
      const { config, response } = error;
      // 如果不是 401 错误，直接抛出异常
      if (response?.status !== 401) {
        throw error;
      }
      // 判断是否启用了 refreshToken 功能
      // 如果没有启用或者已经是重试请求了，直接跳转到重新登录
      if (!enableRefreshToken || config.__isRetryRequest) {
        await doReAuthenticate();
        throw error;
      }
      // 如果正在刷新 token，则将请求加入队列，等待刷新完成
      if (client.isRefreshing) {
        return new Promise((resolve, reject) => {
          client.refreshTokenQueue.push({
            resolve: (newToken: string) => {
              config.headers.Authorization = formatToken(newToken);
              resolve(client.request(config.url, { ...config }));
            },
            reject: (error: unknown) => {
              reject(error);
            },
          });
        });
      }

      // 标记开始刷新 token
      client.isRefreshing = true;
      // 标记当前请求为重试请求，避免无限循环
      config.__isRetryRequest = true;

      try {
        const newToken = await doRefreshToken();

        // 处理队列中的请求
        client.refreshTokenQueue.forEach((callback) => callback.resolve(newToken));
        // 清空队列
        client.refreshTokenQueue = [];

        return client.request(error.config.url, { ...error.config });
      } catch (refreshError) {
        // 如果刷新 token 失败，拒绝队列中所有等待的请求
        client.refreshTokenQueue.forEach((callback) => callback.reject(refreshError));
        client.refreshTokenQueue = [];
        console.error('Refresh token failed, please login again.');
        await doReAuthenticate();

        throw refreshError;
      } finally {
        client.isRefreshing = false;
      }
    },
  };
};

/**
 * 创建「把请求错误翻译成用户可读提示」的响应拦截器。
 *
 * @remarks
 * 判定顺序：先识别主动取消的请求（`axios.isCancel`）并**静默透传**，不弹提示；
 * 再识别网络异常与超时；最后按 HTTP 状态码映射国际化文案，未命中的状态码统一归为服务端错误。
 *
 * 行为约定：本拦截器**只负责提示、不吞异常**——所有分支最终都会
 * `Promise.reject(error)` 把原始错误继续向上抛，业务侧仍可自行 catch 做补偿处理。
 * 提示的实际呈现方式（Message / Notification / 静默上报）由 `makeErrorMessage` 决定，
 * 未传时只做错误透传，不产生任何 UI 反馈。
 *
 * 注意：401 在这里也会产生提示文案，若同时启用了
 * {@link authenticateResponseInterceptor}，需注意拦截器注册顺序以免出现重复提示。
 *
 * @param makeErrorMessage - 展示错误提示的回调，接收本地化后的文案与原始错误对象；省略则不提示
 * @returns 可注册到请求客户端的响应拦截器配置（仅含 `rejected` 分支）
 */
export const errorMessageResponseInterceptor = (
  makeErrorMessage?: MakeErrorMessageFn,
): ResponseInterceptorConfig => {
  return {
    rejected: (error: any) => {
      if (axios.isCancel(error)) {
        return Promise.reject(error);
      }

      const err: string = error?.toString?.() ?? '';
      let errMsg = '';
      if (err?.includes('Network Error')) {
        errMsg = $t('ui.fallback.http.networkError');
      } else if (error?.message?.includes?.('timeout')) {
        errMsg = $t('ui.fallback.http.requestTimeout');
      }
      if (errMsg) {
        makeErrorMessage?.(errMsg, error);
        return Promise.reject(error);
      }

      let errorMessage = '';
      const status = error?.response?.status;

      switch (status) {
        case 400: {
          errorMessage = $t('ui.fallback.http.badRequest');
          break;
        }
        case 401: {
          errorMessage = $t('ui.fallback.http.unauthorized');
          break;
        }
        case 403: {
          errorMessage = $t('ui.fallback.http.forbidden');
          break;
        }
        case 404: {
          errorMessage = $t('ui.fallback.http.notFound');
          break;
        }
        case 408: {
          errorMessage = $t('ui.fallback.http.requestTimeout');
          break;
        }
        case 502: {
          errorMessage = $t('ui.fallback.http.badGateway');
          break;
        }
        case 503: {
          errorMessage = $t('ui.fallback.http.serviceUnavailable');
          break;
        }
        case 504: {
          errorMessage = $t('ui.fallback.http.gatewayTimeout');
          break;
        }
        default: {
          // 兜底：所有其他状态码统一返回服务器错误提示
          errorMessage = $t('ui.fallback.http.internalServerError');
          break;
        }
      }
      makeErrorMessage?.(errorMessage, error);
      return Promise.reject(error);
    },
  };
};
