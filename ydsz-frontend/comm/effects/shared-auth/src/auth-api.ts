/**
 * Auth API — 对齐后端 /api/v1/auth/*
 */
import type { AuthApi } from './types';

import { baseRequestClient, requestClient } from './request-setup';

export { baseRequestClient, requestClient };

/**
 * 登录
 */
export async function loginApi(data: AuthApi.LoginParams) {
  return requestClient.post<AuthApi.LoginResult>(
    '/api/v1/auth/login',
    data,
  );
}

/**
 * 刷新 accessToken
 */
export async function refreshTokenApi(refreshToken: string) {
  return baseRequestClient.post<AuthApi.RefreshTokenResult>(
    '/api/v1/auth/refresh',
    { refreshToken },
  );
}

/**
 * 退出登录
 */
export async function logoutApi() {
  return baseRequestClient.post('/api/v1/auth/logout', {});
}

/**
 * 获取用户权限码
 */
export async function getAccessCodesApi() {
  return requestClient.get<string[]>('/api/v1/auth/codes');
}
