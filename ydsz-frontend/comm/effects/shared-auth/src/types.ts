/**
 * 认证相关类型定义（对齐后端 LoginVO）
 */

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
