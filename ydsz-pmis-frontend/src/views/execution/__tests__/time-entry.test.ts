/**
 * @file 工时填报管理页面 API 集成测试
 * @description 批次 26 P2-20。验证工时填报页面依赖的 API 模块导出完整，
 *   且分页/创建/审批/驳回接口 URL 与 HTTP Method 与后端 TimeEntryController 契约一致。
 *   采用 mock @/utils/request + 导入真实 API 模块的方式（同 api/__tests__/agent.test.ts 约定）。
 * @module views/execution/__tests__/time-entry
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock HTTP 层，拦截真实请求
vi.mock('@/utils/request', () => ({
  request: vi.fn(),
}))

import * as timeEntryApi from '@/api/execution/time-entry'
import { request } from '@/utils/request'

const requestMock = request as unknown as ReturnType<typeof vi.fn>

describe('Time Entry 页面 API (批次 26 P2-20)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('API 模块应导出完整的增删改查与审批方法', () => {
    expect(typeof timeEntryApi.pageTimeEntries).toBe('function')
    expect(typeof timeEntryApi.getTimeEntry).toBe('function')
    expect(typeof timeEntryApi.createTimeEntry).toBe('function')
    expect(typeof timeEntryApi.updateTimeEntry).toBe('function')
    expect(typeof timeEntryApi.approveTimeEntry).toBe('function')
    expect(typeof timeEntryApi.rejectTimeEntry).toBe('function')
    expect(typeof timeEntryApi.deleteTimeEntry).toBe('function')
  })

  it('pageTimeEntries 应调用 GET /execution/time-entry/page', () => {
    requestMock.mockReturnValue({ data: { list: [], total: 0 } })
    timeEntryApi.pageTimeEntries(1, 20)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/execution/time-entry/page')
    expect(call.method).toBe('GET')
  })

  it('createTimeEntry 应调用 POST /execution/time-entry', () => {
    requestMock.mockReturnValue({ data: 1 })
    timeEntryApi.createTimeEntry({} as any)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/execution/time-entry')
    expect(call.method).toBe('POST')
  })

  it('approveTimeEntry 应调用 PUT /execution/time-entry/approve', () => {
    requestMock.mockReturnValue({ data: undefined })
    timeEntryApi.approveTimeEntry({ id: 1 } as any)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/execution/time-entry/approve')
    expect(call.method).toBe('PUT')
  })

  it('rejectTimeEntry 应调用 PUT /execution/time-entry/reject', () => {
    requestMock.mockReturnValue({ data: undefined })
    timeEntryApi.rejectTimeEntry({ id: 1, reason: '异常' } as any)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/execution/time-entry/reject')
    expect(call.method).toBe('PUT')
  })
})
