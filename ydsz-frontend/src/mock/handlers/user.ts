/**
 * @file 用户认证与会话模块 Mock 数据处理器
 * @description 为用户信息、菜单权限、Token 刷新、2FA、会话管理等接口提供 Mock 数据。
 *
 * 对齐 src/api/user/* 的所有 endpoint
 *
 * @module mock/handlers/user
 */
import type { MockHandler } from './types'

/**
 * 用户/认证模块 Mock 处理器集合
 *
 * 覆盖端点:
 * - GET    /user/info                 当前用户信息 (兼容老路径)
 * - GET    /user/menu                 当前用户菜单
 * - GET    /user/permission           当前用户权限码
 * - GET    /users/me                  当前用户信息 (实际端点)
 * - POST   /users/me/password         修改当前用户密码
 * - POST   /auth/refresh              刷新 Token
 * - POST   /user/2fa/bind             2FA 绑定 (生成 secret/otpauthUri)
 * - POST   /user/2fa/confirm          2FA 绑定确认
 * - POST   /user/2fa/verify           2FA 验证
 * - POST   /user/2fa/verify-backup    2FA 备份码验证
 * - POST   /user/2fa/disable          2FA 关闭
 * - GET    /user/2fa/me               2FA 状态查询
 * - GET    /user/2fa/backup-codes     2FA 备份码查询
 * - GET    /user/session/active       在线会话列表
 * - DELETE /user/session/others       踢出其他会话
 * - DELETE /user/session/admin/kick   管理员踢人会话
 *
 * @returns 用户/认证模块所有 Mock 处理器数组
 */
export const userHandlers: MockHandler[] = [
  // 兼容老路径
  {
    method: 'GET',
    path: '/user/info',
    handler: () => ({
      id: 1,
      username: 'admin',
      realName: '系统管理员',
      email: 'admin@ydsz.local',
      phone: '13800000000',
      avatar: '',
      department: '技术中心',
      role: 'SUPER_ADMIN',
    }),
  },
  {
    method: 'GET',
    path: '/user/menu',
    handler: () => [
      { id: 1, name: '系统管理', path: '/system', icon: 'Setting' },
      { id: 2, name: '项目管理', path: '/project', icon: 'Folder' },
      { id: 3, name: '执行管理', path: '/execution', icon: 'Operation' },
      { id: 4, name: '财务管理', path: '/finance', icon: 'Money' },
      { id: 5, name: '报表中心', path: '/report', icon: 'DataAnalysis' },
    ],
  },
  {
    method: 'GET',
    path: '/user/permission',
    handler: () => ['*.*.*'],
  },

  // 实际调用的端点 (src/api/user/index.ts)
  {
    method: 'GET',
    path: '/users/me',
    handler: () => ({
      id: 1,
      username: 'admin',
      realName: '系统管理员',
      email: 'admin@ydsz.local',
      phone: '13800000000',
      avatar: '',
      department: '技术中心',
      role: 'SUPER_ADMIN',
    }),
  },
  {
    method: 'POST',
    path: '/users/me/password',
    handler: () => ({ success: true }),
  },
  {
    method: 'POST',
    path: '/auth/refresh',
    handler: () => ({
      accessToken: 'mock-refreshed-access-token',
      refreshToken: 'mock-refreshed-refresh-token',
      expiresIn: 7200,
    }),
  },

  // 2FA 实际端点 (src/api/user/two-factor.ts)
  {
    method: 'POST',
    path: '/user/2fa/bind',
    handler: () => ({
      secret: 'JBSWY3DPEHPK3PXP',
      otpauthUri: 'otpauth://totp/YDSZ:admin?secret=JBSWY3DPEHPK3PXP&issuer=YDSZ',
    }),
  },
  {
    method: 'POST',
    path: '/user/2fa/confirm',
    handler: () => true,
  },
  {
    method: 'POST',
    path: '/user/2fa/verify',
    handler: () => true,
  },
  {
    method: 'POST',
    path: '/user/2fa/verify-backup',
    handler: () => true,
  },
  {
    method: 'POST',
    path: '/user/2fa/disable',
    handler: () => ({ success: true }),
  },
  {
    method: 'GET',
    path: '/user/2fa/me',
    handler: () => ({
      enabled: false,
      boundAt: null,
      lastUsedAt: null,
      backupCodeCount: 0,
    }),
  },
  {
    method: 'GET',
    path: '/user/2fa/backup-codes',
    handler: () => ['a1b2c3d4', 'e5f6a7b8', 'c9d0e1f2'],
  },

  // 会话实际端点 (src/api/user/session.ts)
  {
    method: 'GET',
    path: '/user/session/active',
    handler: () => [
      {
        sessionId: 'mock-session-1',
        userId: 1,
        username: 'admin',
        userAgent:
          'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0',
        clientIp: '127.0.0.1',
        loginAt: '2026-07-01 10:00:00',
        lastActiveAt: '2026-07-01 11:00:00',
        expireAt: '2026-07-02 10:00:00',
        status: 'ACTIVE',
      },
    ],
  },
  {
    method: 'DELETE',
    path: '/user/session/others',
    handler: () => 0,
  },
  {
    method: 'DELETE',
    path: '/user/session/admin/kick',
    handler: () => ({ success: true }),
  },
]
