/**
 * @file 鉴权模块 Mock 数据处理器
 * @description 为登录、登出、验证码等鉴权相关 API 路径提供 Mock 数据，
 *              支持前端独立联调与权限链路验证
 * @module mock/handlers/auth
 */
import type { MockHandler } from './types'

/**
 * 鉴权模块 Mock 处理器集合
 * @returns {MockHandler[]} 覆盖登录、登出、验证码获取等接口的 Mock 处理器
 */
export const authHandlers: MockHandler[] = [
  // ===== 账号登录：admin/admin123 返回超级管理员令牌，其余账号返回普通令牌 =====
  {
    method: 'POST',
    path: '/auth/login',
    handler: ({ body }) => {
      const b = (body || {}) as { username?: string; password?: string }
      if (b.username === 'admin' && b.password === 'admin123') {
        return {
          accessToken: 'mock-access-token-admin',
          refreshToken: 'mock-refresh-token-admin',
          expiresIn: 7200,
          user: { id: 1, username: 'admin', realName: '系统管理员', role: 'SUPER_ADMIN' },
        }
      }
      return { accessToken: 'mock-access-token', refreshToken: 'mock-refresh', expiresIn: 7200 }
    },
  },
  // ===== 登出：Mock 直接返回成功 =====
  {
    method: 'POST',
    path: '/auth/logout',
    handler: () => ({ success: true }),
  },
  // ===== 图形验证码：返回内联 SVG，便于无后端环境渲染 =====
  {
    method: 'GET',
    path: '/auth/captcha',
    handler: () => ({
      captchaId: 'mock-captcha-id',
      captchaImage:
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="120" height="40"><text x="20" y="28" font-size="24">MOCK</text></svg>',
    }),
  },
]
