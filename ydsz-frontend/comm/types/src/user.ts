/**
 * user 类型定义模块
 *
 * @path comm\types\src\user.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { BasicUserInfo } from '@ydsz-core/typings';

/** 用户信息 */
interface UserInfo extends BasicUserInfo {
  /**
   * 用户描述
   */
  desc: string;
  /**
   * 首页地址
   */
  homePath: string;

  /**
   * accessToken
   */
  token: string;
}

export type { UserInfo };
