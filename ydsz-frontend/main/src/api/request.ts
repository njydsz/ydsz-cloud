/**
 * RequestClient — 主应用复用 @ydsz/shared-auth 的共享请求客户端
 *
 * P2-3: 消除主应用与 shared-auth 的重复代码。
 * 主应用只需提供 doReAuthenticate 和 doRefreshToken 回调，
 * 其余拦截器配置（successCode="A00000" + Bearer Token + refreshToken）由 shared-auth 统一管理。
 */
import type { RequestClientOptions } from '@ydsz/request';

import { preferences } from '@ydsz/preferences';
import { useAccessStore, useTokenStore } from '@ydsz/stores';
import {
  createSharedBaseClient,
  createSharedRequestClient,
} from '@ydsz/shared-auth';

import { useAuthStore } from '#/store/auth';

import { refreshTokenApi } from './core/auth';

const options: RequestClientOptions = {
  responseReturn: 'data',
};

/**
 * 访问令牌失效时的重新认证回调。
 *
 * shared-auth 判定 accessToken / refreshToken 失效或过期时触发：清空本地令牌，
 * 若配置为弹窗模式且已完成首次访问校验则弹出登录过期提示，否则直接登出。
 */
async function doReAuthenticate() {
  console.warn('Access token or refresh token is invalid or expired. ');
  const accessStore = useAccessStore();
  const tokenStore = useTokenStore();
  tokenStore.setAccessToken(null);
  if (
    preferences.app.loginExpiredMode === 'modal' &&
    accessStore.isAccessChecked
  ) {
    tokenStore.setLoginExpired(true);
  } else {
    const authStore = useAuthStore();
    await authStore.logout();
  }
}

/**
 * 刷新访问令牌回调。
 *
 * 使用 useTokenStore 的 refreshToken 调用后端刷新接口，成功则更新 accessToken；
 * 无 refreshToken 时返回 null，交由 shared-auth 触发 {@link doReAuthenticate}。
 */
async function doRefreshToken() {
  const tokenStore = useTokenStore();
  const refreshToken = tokenStore.refreshToken;
  if (!refreshToken) {
    return null;
  }
  const resp = await refreshTokenApi(refreshToken);
  const newToken = resp.data?.accessToken || resp.data as unknown as string;
  if (typeof newToken === 'string') {
    tokenStore.setAccessToken(newToken);
  }
  return newToken;
}

/** 主应用共享请求客户端：携带鉴权拦截器，响应统一只返回 data 字段。 */
export const requestClient = createSharedRequestClient(
  doReAuthenticate,
  doRefreshToken,
  options,
);

/** 基础请求客户端：不含鉴权拦截器，用于登录、刷新令牌等公共接口。 */
export const baseRequestClient = createSharedBaseClient();
