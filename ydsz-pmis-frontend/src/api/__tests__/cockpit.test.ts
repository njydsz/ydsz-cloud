/**
 * @file 经营驾驶舱 API 调用层 单元测试
 * @description 验证驾驶舱(cockpit)概览、EVM 健康、bench 成本、利用率、下钻、
 *   高管看板、预警、KPI 趋势等 REST 调用正确触发且 URL / Method / 参数透传正确.
 * @module api/__tests__/cockpit
 * @author ydsz-pmis-team
 * @since 1.0.0 (批次18)
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock @/utils/request: 拦截所有 HTTP 请求, 通过 requestMock 断言 url / method / params
vi.mock('@/utils/request', () => ({
  request: vi.fn(),
}))

import {
  getCockpitOverview,
  getEvmHealthDistribution,
  getBenchCostSummary,
  getUtilizationSummary,
  drillByDept,
  drillByProjectType,
  drillByCustomer,
  getContractYearlyTrend,
  // 批次18 新增
  getAlertSummary,
  getProjectGroupOverview,
  getExecutiveOverview,
  getKpiTrend,
} from '@/api/execution/cockpit'

const { request } = await import('@/utils/request')
const requestMock = request as unknown as ReturnType<typeof vi.fn>

describe('cockpit API 调用层', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getCockpitOverview 应调用 GET /execution/cockpit/overview 并透传 period', () => {
    requestMock.mockReturnValue({ data: {} })
    getCockpitOverview('2026-07', { department: 1 })
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/execution/cockpit/overview')
    expect(call.method).toBe('GET')
    expect(call.params.period).toBe('2026-07')
    expect(call.params.department).toBe(1)
  })

  it('getCockpitOverview 在不传参时只透传 undefined', () => {
    requestMock.mockReturnValue({ data: {} })
    getCockpitOverview()
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/execution/cockpit/overview')
  })

  it('getEvmHealthDistribution 应使用 GET + period 参数', () => {
    requestMock.mockReturnValue({ data: { RED: 1, YELLOW: 2, NORMAL: 3 } })
    getEvmHealthDistribution('2026-06')
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/execution/cockpit/evm-health')
    expect(call.method).toBe('GET')
    expect(call.params.period).toBe('2026-06')
  })

  it('getBenchCostSummary 应调用 GET /execution/cockpit/bench-cost', () => {
    requestMock.mockReturnValue({ data: {} })
    getBenchCostSummary()
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/execution/cockpit/bench-cost')
    expect(call.method).toBe('GET')
  })

  it('getUtilizationSummary 应调用 GET /execution/cockpit/utilization', () => {
    requestMock.mockReturnValue({ data: {} })
    getUtilizationSummary()
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/execution/cockpit/utilization')
    expect(call.method).toBe('GET')
  })

  it('drillByDept 应使用 GET + period 参数', () => {
    requestMock.mockReturnValue({ data: [] })
    drillByDept('2026-07')
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/execution/cockpit/drill/dept')
    expect(call.params.period).toBe('2026-07')
  })

  it('drillByProjectType 应使用 GET + period 参数', () => {
    requestMock.mockReturnValue({ data: [] })
    drillByProjectType('2026-07')
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/execution/cockpit/drill/project-type')
    expect(call.params.period).toBe('2026-07')
  })

  it('drillByCustomer 应使用 GET + period 参数', () => {
    requestMock.mockReturnValue({ data: [] })
    drillByCustomer('2026-07')
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/execution/cockpit/drill/customer')
    expect(call.params.period).toBe('2026-07')
  })

  it('getContractYearlyTrend 应调用 GET /execution/cockpit/contract-yearly-trend', () => {
    requestMock.mockReturnValue({ data: {} })
    getContractYearlyTrend()
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/execution/cockpit/contract-yearly-trend')
    expect(call.method).toBe('GET')
  })
})

describe('cockpit API 调用层 (批次18 增量)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getAlertSummary 应使用 GET /execution/cockpit/alerts + period', () => {
    requestMock.mockReturnValue({ data: { redCount: 0, yellowCount: 0, totalCount: 0, events: [] } })
    getAlertSummary('2026-07')
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/execution/cockpit/alerts')
    expect(call.method).toBe('GET')
    expect(call.params.period).toBe('2026-07')
  })

  it('getAlertSummary 不传 period 时不应传参', () => {
    requestMock.mockReturnValue({ data: null })
    getAlertSummary()
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/execution/cockpit/alerts')
    expect(call.params.period).toBeUndefined()
  })

  it('getProjectGroupOverview 应使用 GET /execution/cockpit/project-group + period', () => {
    requestMock.mockReturnValue({ data: [] })
    getProjectGroupOverview('2026-07')
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/execution/cockpit/project-group')
    expect(call.method).toBe('GET')
    expect(call.params.period).toBe('2026-07')
  })

  it('getExecutiveOverview 应使用 GET /execution/cockpit/executive + period', () => {
    requestMock.mockReturnValue({ data: { healthScore: 88, healthGrade: 'B' } })
    getExecutiveOverview('2026-07')
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/execution/cockpit/executive')
    expect(call.method).toBe('GET')
    expect(call.params.period).toBe('2026-07')
  })

  it('getKpiTrend 应使用 GET /execution/cockpit/kpi-trend + 默认 12 月', () => {
    requestMock.mockReturnValue({ data: { periods: [], contractAmountSeries: [] } })
    getKpiTrend()
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/execution/cockpit/kpi-trend')
    expect(call.method).toBe('GET')
    expect(call.params.months).toBe(12)
  })

  it('getKpiTrend 自定义 months 参数应被透传', () => {
    requestMock.mockReturnValue({ data: { periods: [] } })
    getKpiTrend(6)
    const call = requestMock.mock.calls[0][0]
    expect(call.params.months).toBe(6)
  })
})
