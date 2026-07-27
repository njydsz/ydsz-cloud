import type { UserInfo } from '@ydsz/types';

import { requestClient } from '#/api/request';

/**
 * 获取当前登录用户信息
 */
export async function getUserInfoApi() {
  return requestClient.get<UserInfo>('/api/v1/auth/userinfo');
}
