/**
 * @file AI Agent API 调用层 单元测试
 * @description 验证 AI Agent 编排(orchestration)与预测(prediction)相关 REST 调用,
 *   确保方法名 / URL / HTTP Method 一一对应, 参数正确透传.
 * @module api/__tests__/agent
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  request: vi.fn(),
}))

import * as orchestrationApi from '@/api/agent/orchestration'
import * as predictionApi from '@/api/agent/prediction'

const { request } = await import('@/utils/request')
const requestMock = request as unknown as ReturnType<typeof vi.fn>

/**
 * AI Agent API 调用层测试
 *
 * 验证编排 / 预测相关 REST 调用都正确触发且方法名/URL/Method 一一对应。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
describe('agent API 调用层 (orchestration)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('coordinate 应调用 POST /agent/orchestration/coordinate', () => {
    requestMock.mockReturnValue({ data: { mode: 'SEQUENTIAL' } })
    orchestrationApi.coordinate({
      mode: 'SEQUENTIAL',
      agentTypes: ['RISK_WARNING'],
    } as any)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/agent/orchestration/coordinate')
    expect(call.method).toBe('POST')
    expect(call.data.mode).toBe('SEQUENTIAL')
  })

  it('listAgents 应调用 GET /agent/orchestration/agents', () => {
    requestMock.mockReturnValue({ data: [] })
    orchestrationApi.listAgents()
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/agent/orchestration/agents')
    expect(call.method).toBe('GET')
  })

  it('listModes 应调用 GET /agent/orchestration/modes', () => {
    requestMock.mockReturnValue({ data: [] })
    orchestrationApi.listModes()
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/agent/orchestration/modes')
    expect(call.method).toBe('GET')
  })
})

describe('agent API 调用层 (prediction)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('runAgent 应调用 POST /agent/run', () => {
    requestMock.mockReturnValue({ data: { id: 1 } })
    predictionApi.runAgent({ agentType: 'RISK_WARNING' } as any)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/agent/run')
    expect(call.method).toBe('POST')
  })

  it('runAgentAsync 应调用 POST /agent/run-async', () => {
    requestMock.mockReturnValue({ data: null })
    predictionApi.runAgentAsync({ agentType: 'PROFIT_FORECAST' } as any)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/agent/run-async')
    expect(call.method).toBe('POST')
  })

  it('getById 应使用 GET + 路径参数', () => {
    requestMock.mockReturnValue({ data: { id: 1 } })
    predictionApi.getById(42)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/agent/42')
    expect(call.method).toBe('GET')
  })

  it('page 应使用 GET + query 参数', () => {
    requestMock.mockReturnValue({ data: { list: [], total: 0 } })
    predictionApi.page(1, 20, { agentType: 'RISK_WARNING' })
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/agent/page')
    expect(call.method).toBe('GET')
    expect(call.params.page).toBe(1)
    expect(call.params.size).toBe(20)
    expect(call.params.agentType).toBe('RISK_WARNING')
  })

  it('recent 应使用 GET + limit 默认', () => {
    requestMock.mockReturnValue({ data: [] })
    predictionApi.recent({ limit: 10 })
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/agent/recent')
    expect(call.method).toBe('GET')
    expect(call.params.limit).toBe(10)
  })

  it('aggregateByType 应使用 GET', () => {
    requestMock.mockReturnValue({ data: [] })
    predictionApi.aggregateByType(1)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/agent/aggregate/type')
    expect(call.params.tenantId).toBe(1)
  })

  it('countByAlertLevel 应使用 GET + 等级过滤', () => {
    requestMock.mockReturnValue({ data: 0 })
    predictionApi.countByAlertLevel({ alertLevel: 'RED' })
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/agent/count')
    expect(call.params.alertLevel).toBe('RED')
  })

  it('inMemory 应使用 POST + agentType 路径参数', () => {
    requestMock.mockReturnValue({ data: {} })
    predictionApi.inMemory('RISK_WARNING', { foo: 1 })
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/agent/in-memory')
    expect(call.method).toBe('POST')
    expect(call.params.agentType).toBe('RISK_WARNING')
  })
})
