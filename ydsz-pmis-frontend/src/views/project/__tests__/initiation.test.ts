/**
 * @file 立项管理页面 API 集成测试
 * @description 批次 26 P2-20。验证立项页面依赖的 API 模块导出完整，
 *   且分页/创建/审批流启动接口 URL 与 HTTP Method 与后端 InitiationController 契约一致。
 *   采用 mock @/utils/request + 导入真实 API 模块的方式（同 api/__tests__/agent.test.ts 约定）。
 * @module views/project/__tests__/initiation
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock HTTP 层，拦截真实请求
vi.mock('@/utils/request', () => ({
  request: vi.fn(),
}))

import * as initiationApi from '@/api/project/initiation'
import { request } from '@/utils/request'

const requestMock = request as unknown as ReturnType<typeof vi.fn>

describe('Initiation 页面 API (批次 26 P2-20)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('API 模块应导出完整的增删改查与审批流启动方法', () => {
    expect(typeof initiationApi.pageInitiations).toBe('function')
    expect(typeof initiationApi.getInitiation).toBe('function')
    expect(typeof initiationApi.createInitiation).toBe('function')
    expect(typeof initiationApi.changeInitiationStage).toBe('function')
    expect(typeof initiationApi.deleteInitiation).toBe('function')
    expect(typeof initiationApi.startInitiationProcess).toBe('function')
  })

  it('pageInitiations 应调用 GET /project/initiation/page', () => {
    requestMock.mockReturnValue({ data: { list: [], total: 0 } })
    initiationApi.pageInitiations(1, 20)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/project/initiation/page')
    expect(call.method).toBe('GET')
  })

  it('createInitiation 应调用 POST /project/initiation', () => {
    requestMock.mockReturnValue({ data: 1 })
    initiationApi.createInitiation({} as any)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/project/initiation')
    expect(call.method).toBe('POST')
  })

  it('startInitiationProcess 应调用 POST /project/initiation/{id}/start-process', () => {
    requestMock.mockReturnValue({ data: 'process-001' })
    initiationApi.startInitiationProcess(1, 2)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/project/initiation/1/start-process')
    expect(call.method).toBe('POST')
    expect(call.params.initiatorId).toBe(2)
  })
})
