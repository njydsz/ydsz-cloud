/**
 * @file 商机管理页面 API 集成测试
 * @description 批次 26 P2-20。验证商机页面依赖的 API 模块导出完整，
 *   且分页/创建/删除/转立项接口 URL 与 HTTP Method 与后端 OpportunityController 契约一致。
 *   采用 mock @/utils/request + 导入真实 API 模块的方式（同 api/__tests__/agent.test.ts 约定）。
 * @module views/project/__tests__/opportunity
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock HTTP 层，拦截真实请求
vi.mock('@/utils/request', () => ({
  request: vi.fn(),
}))

import * as opportunityApi from '@/api/project/opportunity'
import { request } from '@/utils/request'

const requestMock = request as unknown as ReturnType<typeof vi.fn>

describe('Opportunity 页面 API (批次 26 P2-20)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('API 模块应导出完整的增删改查与转立项方法', () => {
    expect(typeof opportunityApi.pageOpportunities).toBe('function')
    expect(typeof opportunityApi.getOpportunity).toBe('function')
    expect(typeof opportunityApi.createOpportunity).toBe('function')
    expect(typeof opportunityApi.updateOpportunity).toBe('function')
    expect(typeof opportunityApi.deleteOpportunity).toBe('function')
    expect(typeof opportunityApi.convertToInitiation).toBe('function')
  })

  it('pageOpportunities 应调用 GET /project/opportunity/page', () => {
    requestMock.mockReturnValue({ data: { list: [], total: 0 } })
    opportunityApi.pageOpportunities(1, 20)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/project/opportunity/page')
    expect(call.method).toBe('GET')
  })

  it('createOpportunity 应调用 POST /project/opportunity', () => {
    requestMock.mockReturnValue({ data: 1 })
    opportunityApi.createOpportunity({} as any)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/project/opportunity')
    expect(call.method).toBe('POST')
  })

  it('convertToInitiation 应调用 POST /project/opportunity/{id}/convert-to-initiation', () => {
    requestMock.mockReturnValue({ data: 1 })
    opportunityApi.convertToInitiation(5)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/project/opportunity/5/convert-to-initiation')
    expect(call.method).toBe('POST')
  })
})
