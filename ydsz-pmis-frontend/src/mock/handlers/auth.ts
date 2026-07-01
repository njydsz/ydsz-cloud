/**
 * Auth 鉴权 mock
 */
import type { MockHandler } from './types'

export const authHandlers: MockHandler[] = [
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
  {
    method: 'POST',
    path: '/auth/logout',
    handler: () => ({ success: true }),
  },
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
