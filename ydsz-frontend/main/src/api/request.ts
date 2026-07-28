/**
 * RequestClient — 主应用复用 @ydsz/shared-auth 的共享请求客户端
 *
 * P2-3: 消除主应用与 shared-auth 的重复代码。
 * 主应用只需提供 doReAuthenticate 和 doRefreshToken 回调，
 * 其余拦截器配置（successCode="A00000" + Bearer Token + refreshToken）由 shared-auth 统一管理。
 */
import type { RequestClientOptions } from '@ydsz/request';

import { preferences } from '@ydsz/preferences';
import { useAccessStore } from '@ydsz/stores';
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
 * 重新认证逻辑
 */
async function doReAuthenticate() {
  console.warn('Access token or refresh token is invalid or expired. ');
  const accessStore = useAccessStore();
  const authStore = useAuthStore();
  accessStore.setAccessToken(null);
  if (
    preferences.app.loginExpiredMode === 'modal' &&
    accessStore.isAccessChecked
  ) {
    accessStore.setLoginExpired(true);
  } else {
    await authStore.logout();
  }
}

/**
 * 刷新token逻辑
 */
async function doRefreshToken() {
  const accessStore = useAccessStore();
  const refreshToken = accessStore.refreshToken;
  if (!refreshToken) {
    return null;
  }
  const resp = await refreshTokenApi(refreshToken);
  const newToken = resp.data?.accessToken || resp.data as unknown as string;
  if (typeof newToken === 'string') {
    accessStore.setAccessToken(newToken);
  }
  return newToken;
}

export const requestClient = createSharedRequestClient(
  doReAuthenticate,
  doRefreshToken,
  options,
);

export const baseRequestClient = createSharedBaseClient();
