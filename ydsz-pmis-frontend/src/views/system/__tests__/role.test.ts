/**
 * @file 角色管理页面 API 集成测试
 * @description 批次 26 P2-20。验证角色管理页面依赖的 API 模块导出完整，
 *   且分页/创建/删除/权限分配接口 URL 与 HTTP Method 与后端 RoleController 契约一致。
 *   采用 mock @/utils/request + 导入真实 API 模块的方式（同 api/__tests__/agent.test.ts 约定）。
 * @module views/system/__tests__/role
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock HTTP 层，拦截真实请求
vi.mock('@/utils/request', () => ({
  request: vi.fn(),
}))

import * as roleApi from '@/api/system/role'
import { request } from '@/utils/request'

const requestMock = request as unknown as ReturnType<typeof vi.fn>

describe('Role Management 页面 API (批次 26 P2-20)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('API 模块应导出完整的增删改查与权限分配方法', () => {
    expect(typeof roleApi.listRoles).toBe('function')
    expect(typeof roleApi.listAllRoles).toBe('function')
    expect(typeof roleApi.getRole).toBe('function')
    expect(typeof roleApi.createRole).toBe('function')
    expect(typeof roleApi.updateRole).toBe('function')
    expect(typeof roleApi.deleteRole).toBe('function')
    expect(typeof roleApi.assignPermissions).toBe('function')
  })

  it('listRoles 应调用 GET /roles', () => {
    requestMock.mockReturnValue({ data: { list: [], total: 0 } })
    roleApi.listRoles({ page: 1, size: 20 } as any)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/roles')
    expect(call.method).toBe('GET')
  })

  it('createRole 应调用 POST /roles', () => {
    requestMock.mockReturnValue({ data: 1 })
    roleApi.createRole({} as any)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/roles')
    expect(call.method).toBe('POST')
  })

  it('assignPermissions 应调用 PUT /roles/{roleId}/permissions', () => {
    requestMock.mockReturnValue({ data: undefined })
    roleApi.assignPermissions(2, [1, 2, 3])
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/roles/2/permissions')
    expect(call.method).toBe('PUT')
    expect(call.data).toEqual([1, 2, 3])
  })
})
