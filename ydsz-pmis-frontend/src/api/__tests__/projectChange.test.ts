/**
 * 项目变更 API 调用层测试（批次 19 补全）
 *
 * 验证 8 个 REST 调用都正确触发且方法名/URL/Method 一一对应。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  request: vi.fn(),
}))

import * as changeApi from '@/api/project/change'

const { request } = await import('@/utils/request')
const requestMock = request as unknown as ReturnType<typeof vi.fn>

describe('project/change API 调用层', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('pageProjectChanges 应调用 GET /project/change/page', () => {
    requestMock.mockReturnValue({ data: { list: [], total: 0 } })
    changeApi.pageProjectChanges(1, 10, { keyword: 'CHG' })
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/project/change/page')
    expect(call.method).toBe('GET')
    expect(call.params.page).toBe(1)
    expect(call.params.size).toBe(10)
    expect(call.params.keyword).toBe('CHG')
  })

  it('getProjectChange 应调用 GET /project/change/{id}', () => {
    requestMock.mockReturnValue({ data: { id: 1 } })
    changeApi.getProjectChange(42)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/project/change/42')
    expect(call.method).toBe('GET')
  })

  it('createProjectChange 应调用 POST /project/change', () => {
    requestMock.mockReturnValue({ data: 99 })
    changeApi.createProjectChange({
      changeCode: 'CHG-1',
      initiationId: 1,
      changeType: 'SCOPE',
      changeTitle: '范围调整',
    } as any)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/project/change')
    expect(call.method).toBe('POST')
    expect(call.data.changeCode).toBe('CHG-1')
  })

  it('changeProjectChangeStatus 应调用 PUT /project/change/status', () => {
    requestMock.mockReturnValue({ data: null })
    changeApi.changeProjectChangeStatus({ id: 1, targetStatus: 'APPROVED' })
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/project/change/status')
    expect(call.method).toBe('PUT')
    expect(call.data.targetStatus).toBe('APPROVED')
  })

  it('deleteProjectChange 应调用 DELETE /project/change/{id}', () => {
    requestMock.mockReturnValue({ data: null })
    changeApi.deleteProjectChange(7)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/project/change/7')
    expect(call.method).toBe('DELETE')
  })

  it('listProjectChangesByInitiation 应调用 GET /project/change/list-by-initiation/{id}', () => {
    requestMock.mockReturnValue({ data: [] })
    changeApi.listProjectChangesByInitiation(5)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/project/change/list-by-initiation/5')
    expect(call.method).toBe('GET')
  })

  it('aggregateProjectChangeByType 应调用 GET /project/change/aggregate/type', () => {
    requestMock.mockReturnValue({ data: [] })
    changeApi.aggregateProjectChangeByType(1)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/project/change/aggregate/type')
    expect(call.method).toBe('GET')
    expect(call.params.tenantId).toBe(1)
  })

  it('countMajorProjectChange 应调用 GET /project/change/major-count/{id}', () => {
    requestMock.mockReturnValue({ data: 2 })
    changeApi.countMajorProjectChange(3)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/project/change/major-count/3')
    expect(call.method).toBe('GET')
  })
})
