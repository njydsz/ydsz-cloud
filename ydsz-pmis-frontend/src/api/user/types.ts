/**
 * 登录参数
 */
export interface LoginParams {
  username: string
  password: string
  captchaKey?: string
  captchaCode?: string
  rememberMe?: boolean
}

/**
 * 登录结果
 */
export interface LoginResult {
  token: string
  refreshToken: string
  expiresIn: number
}

/**
 * 用户信息
 */
export interface UserInfo {
  id: number
  username: string
  realName: string
  email?: string
  phone?: string
  avatar?: string
  gender?: 'M' | 'F' | 'U'
  departmentId?: number
  departmentName?: string
  positionId?: number
  positionName?: string
  levelCode?: string
  levelName?: string
  lastLoginTime?: string
  roles: string[]
  permissions: string[]
}
