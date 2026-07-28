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

  export interface RefreshTokenResult {
    accessToken: string;
    refreshToken: string;
    tokenType: string;
    expiresIn: number;
  }
}

/**
 * 登录
 */
export async function loginApi(data: AuthApi.LoginParams) {
  return requestClient.post<AuthApi.LoginResult>('/api/v1/auth/login', data);
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
