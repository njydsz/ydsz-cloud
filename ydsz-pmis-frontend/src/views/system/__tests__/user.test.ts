/**
 * @file 用户管理页面 API 集成测试
 * @description 批次 26 P2-20。验证用户管理页面依赖的 API 模块导出完整，
 *   且分页/创建/删除/重置密码接口 URL 与 HTTP Method 与后端 UserController 契约一致。
 *   采用 mock @/utils/request + 导入真实 API 模块的方式（同 api/__tests__/agent.test.ts 约定）。
 * @module views/system/__tests__/user
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock HTTP 层，拦截真实请求
vi.mock('@/utils/request', () => ({
  request: vi.fn(),
}))

import * as userApi from '@/api/system/user'
import { request } from '@/utils/request'

const requestMock = request as unknown as ReturnType<typeof vi.fn>

describe('User Management 页面 API (批次 26 P2-20)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('API 模块应导出完整的增删改查与重置密码方法', () => {
    expect(typeof userApi.listUsers).toBe('function')
    expect(typeof userApi.getUser).toBe('function')
    expect(typeof userApi.createUser).toBe('function')
    expect(typeof userApi.updateUser).toBe('function')
    expect(typeof userApi.deleteUser).toBe('function')
    expect(typeof userApi.resetPassword).toBe('function')
  })

  it('listUsers 应调用 GET /users', () => {
    requestMock.mockReturnValue({ data: { list: [], total: 0 } })
    userApi.listUsers({ page: 1, size: 20 } as any)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/users')
    expect(call.method).toBe('GET')
  })

  it('createUser 应调用 POST /users', () => {
    requestMock.mockReturnValue({ data: 1 })
    userApi.createUser({} as any)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/users')
    expect(call.method).toBe('POST')
  })

  it('deleteUser 应调用 DELETE /users/{id}', () => {
    requestMock.mockReturnValue({ data: undefined })
    userApi.deleteUser(3)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/users/3')
    expect(call.method).toBe('DELETE')
  })

  it('resetPassword 应调用 POST /users/{id}/reset-password', () => {
    requestMock.mockReturnValue({ data: undefined })
    userApi.resetPassword(3, 'newPwd123')
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/users/3/reset-password')
    expect(call.method).toBe('POST')
    expect(call.params.password).toBe('newPwd123')
  })
})
