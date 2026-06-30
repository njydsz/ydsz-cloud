/**
 * 登录参数
 */
export interface LoginParams {
  username: string
  password: string
  captchaKey?: string
  captchaCode?: string
  rememberMe?: boolean
  /** 2FA TOTP 一次性码 */
  otp?: string
  /** 备份码（与 otp 互斥） */
  backupCode?: string
}

/**
 * 登录结果
 */
export interface LoginResult {
  accessToken: string
  refreshToken?: string
  /** 过期时间（毫秒） */
  expireAt?: number
  /** 会话 ID */
  sessionId?: string
  userId?: number
  username?: string
  /** 是否需要 2FA 二次验证 */
  mfaRequired?: boolean
  /** 2FA 已通过 */
  mfaPassed?: boolean
  dataScope?: string
  // 兼容旧字段
  token?: string
  expiresIn?: number
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
