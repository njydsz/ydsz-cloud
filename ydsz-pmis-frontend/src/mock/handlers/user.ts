/**
 * 用户模块 mock (用户/角色/权限/部门)
 *
 * 对齐 src/api/user/* 的所有 endpoint
 */
import type { MockHandler } from './types'

export const userHandlers: MockHandler[] = [
  // 兼容老路径
  {
    method: 'GET',
    path: '/user/info',
    handler: () => ({
      id: 1,
      username: 'admin',
      realName: '系统管理员',
      email: 'admin@pmis.local',
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
      email: 'admin@pmis.local',
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
      otpauthUri: 'otpauth://totp/PMIS:admin?secret=JBSWY3DPEHPK3PXP&issuer=PMIS',
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
