/**
 * User API — 对齐后端 /api/v1/auth/userinfo
 */
import type { UserInfo } from '@ydsz/types';

import { requestClient } from './request-setup';

/**
 * 获取当前登录用户信息
 */
export async function getUserInfoApi() {
  return requestClient.get<UserInfo>('/api/v1/auth/userinfo');
}
