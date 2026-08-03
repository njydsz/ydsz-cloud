/**
 * auth API 接口定义
 *
 * @path main\src\api\core\auth.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { baseRequestClient, requestClient } from '#/api/request';

export namespace AuthApi {
  /** 登录接口参数 */
  export interface LoginParams {
    username?: string;
    password?: string;
    captcha?: string;
    captchaKey?: string;
  }

  /** 登录接口返回值（对齐后端 LoginVO） */
  export interface LoginResult {
    accessToken: string;
    refreshToken: string;
    tokenType: string;
    expiresIn: number;
    scope: string;
    userInfo: UserInfoVO;
  }

  /** 用户信息（对齐后端 LoginVO.UserInfoVO） */
  export interface UserInfoVO {
    userId: string;
    username: string;
    realName: string;
    roleCode?: string;
    roleName?: string;
    tenantId?: string;
    avatar?: string;
    email?: string;
    phone?: string;
    deptId?: string;
    deptName?: string;
    roles?: string[];
    permissions?: string[];
  }

  /**
   * 刷新令牌接口返回结果。
   */
  export interface RefreshTokenResult {
    accessToken: string;
    refreshToken: string;
    tokenType: string;
    expiresIn: number;
  }
}

/**
 * 用户登录。
 *
 * @param data - 登录参数（用户名、密码、验证码等）
 * @returns 登录结果，含 accessToken / refreshToken 与用户信息
 */
export async function loginApi(data: AuthApi.LoginParams) {
  return requestClient.post<AuthApi.LoginResult>('/api/v1/auth/login', data);
}

/**
 * 使用 refreshToken 刷新访问令牌。
 *
 * @param refreshToken - 刷新令牌
 * @returns 新的令牌信息（accessToken / refreshToken / 过期时间）
 */
export async function refreshTokenApi(refreshToken: string) {
  return baseRequestClient.post<AuthApi.RefreshTokenResult>(
    '/api/v1/auth/refresh',
    { refreshToken },
  );
}

/**
 * 退出登录，使服务端会话失效。
 *
 * @returns 请求响应
 */
export async function logoutApi() {
  return baseRequestClient.post('/api/v1/auth/logout', {});
}

/**
 * 获取当前用户的权限码列表（用于前端按钮级权限控制）。
 *
 * @returns 权限码字符串数组
 */
export async function getAccessCodesApi() {
  return requestClient.get<string[]>('/api/v1/auth/codes');
}
