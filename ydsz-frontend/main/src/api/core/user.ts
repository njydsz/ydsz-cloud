/**
 * user API 接口定义
 *
 * @path main\src\api\core\user.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { UserInfo } from '@ydsz/types';

import { requestClient } from '#/api/request';

/**
 * 获取当前登录用户信息。
 *
 * @returns 当前用户的基础信息
 */
export async function getUserInfoApi() {
  return requestClient.get<UserInfo>('/api/v1/auth/userinfo');
}
