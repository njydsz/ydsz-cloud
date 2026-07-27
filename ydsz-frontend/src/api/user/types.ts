/**
 * @file 用户认证相关类型定义
 * @description 定义登录参数、登录结果与当前用户信息的类型，
 *              与后端 AuthController / UserController 返回结构对齐。
 * @module api/user/types
 */

/**
 * 登录参数
 */
export interface LoginParams {
  /** 用户名 */
  username: string
  /** 密码（明文，由 HTTPS 传输） */
  password: string
  /** 图形验证码 key（来自 getCaptchaApi） */
  captchaKey?: string
  /** 用户输入的图形验证码 */
  captchaCode?: string
  /** 是否记住我（影响 refreshToken 有效期） */
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
  /** 访问令牌 */
  accessToken: string
  /** 刷新令牌，用于续期 */
  refreshToken?: string
  /** 过期时间（毫秒） */
  expireAt?: number
  /** 会话 ID */
  sessionId?: string
  /** 用户 ID */
  userId?: number
  /** 用户名 */
  username?: string
  /** 是否需要 2FA 二次验证 */
  mfaRequired?: boolean
  /** 2FA 已通过 */
  mfaPassed?: boolean
  /** 数据权限范围 */
  dataScope?: string
  // 兼容旧字段
  /** 兼容旧版 token 字段 */
  token?: string
  /** 兼容旧版过期秒数 */
  expiresIn?: number
}

/**
 * 用户信息
 */
export interface UserInfo {
  /** 用户 ID */
  id: string
  /** 用户名（登录账号） */
  username: string
  /** 真实姓名 */
  realName: string
  /** 邮箱 */
  email?: string
  /** 手机号 */
  phone?: string
  /** 头像 URL */
  avatar?: string
  /** 性别：M 男 / F 女 / U 未知 */
  gender?: 'M' | 'F' | 'U'
  /** 部门 ID */
  departmentId?: number
  /** 部门名称 */
  departmentName?: string
  /** 岗位 ID */
  positionId?: number
  /** 岗位名称 */
  positionName?: string
  /** 职级编码 */
  levelCode?: string
  /** 职级名称 */
  levelName?: string
  /** 最后登录时间（ISO 8601） */
  lastLoginTime?: string
  /** 角色编码列表 */
  roles: string[]
  /** 权限码列表（用于前端按钮/路由鉴权） */
  permissions: string[]
}
