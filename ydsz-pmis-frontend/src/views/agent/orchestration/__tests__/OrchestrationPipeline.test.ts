import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import OrchestrationPipeline from '../components/OrchestrationPipeline.vue'
import type { OrchestrationResult } from '@/api/agent/orchestration/types'

function buildResult(overrides: Partial<OrchestrationResult> = {}): OrchestrationResult {
  return {
    mode: 'SEQUENTIAL',
    agentCount: 2,
    totalCostMs: 25,
    note: '',
    executedAgents: ['RISK_WARNING', 'PROFIT_FORECAST'],
    agentResults: {
      RISK_WARNING: { agentType: 'RISK_WARNING', alertLevel: 'YELLOW', score: 60, confidence: 0.8 },
      PROFIT_FORECAST: { agentType: 'PROFIT_FORECAST', alertLevel: 'RED', score: 90, confidence: 0.9 },
    },
    finalResult: { agentType: 'PROFIT_FORECAST', alertLevel: 'RED', score: 90, confidence: 0.9 },
    trace: [],
    ...overrides,
  }
}

describe('OrchestrationPipeline 编排流程图', () => {
  it('SEQUENTIAL 模式 - 渲染 N 个节点 + N-1 条边', () => {
    const wrapper = mount(OrchestrationPipeline, {
      props: { mode: 'SEQUENTIAL', agentTypes: ['A', 'B', 'C'], result: null },
    })
    const rects = wrapper.findAll('rect')
    const paths = wrapper.findAll('g.edges path')
    expect(rects.length).toBe(3)
    expect(paths.length).toBe(2)
  })

  it('PARALLEL 模式 - 渲染 Agent 节点 + 协调器节点 + 汇聚边', () => {
    const wrapper = mount(OrchestrationPipeline, {
      props: { mode: 'PARALLEL', agentTypes: ['A', 'B'], result: null },
    })
    const rects = wrapper.findAll('rect')
    expect(rects.length).toBeGreaterThanOrEqual(3) // 2 agent + 1 coordinator
  })

  it('VOTING 模式 - 渲染 Agent + 融合器 + 最终结果', () => {
    const wrapper = mount(OrchestrationPipeline, {
      props: { mode: 'VOTING', agentTypes: ['A', 'B'], result: null },
    })
    const rects = wrapper.findAll('rect')
    expect(rects.length).toBeGreaterThanOrEqual(4) // 2 agent + 1 fuse + 1 final
  })

  it('CASCADE 模式 - 渲染 N 个节点 + N-1 条边', () => {
    const wrapper = mount(OrchestrationPipeline, {
      props: { mode: 'CASCADE', agentTypes: ['A', 'B', 'C', 'D'], result: null },
    })
    const rects = wrapper.findAll('rect')
    expect(rects.length).toBe(4)
  })

  it('agentTypes 为空 - 渲染 ? 占位 + 单节点', () => {
    const wrapper = mount(OrchestrationPipeline, {
      props: { mode: 'SEQUENTIAL', agentTypes: [], result: null },
    })
    // 实际渲染 '?' 占位（一个矩形 + '?' 标签），非 ElEmpty
    const rects = wrapper.findAll('rect')
    expect(rects.length).toBe(1)
    expect(wrapper.text()).toContain('?')
  })

  it('有 result 时 - 节点按告警等级着色', () => {
    const wrapper = mount(OrchestrationPipeline, {
      props: {
        mode: 'SEQUENTIAL',
        agentTypes: ['RISK_WARNING', 'PROFIT_FORECAST'],
        result: buildResult(),
      },
    })
    const rects = wrapper.findAll('rect')
    // 第一个 YELLOW / 第二个 RED，颜色应分别为 #E6A23C / #F56C6C
    expect(rects[0].attributes('fill')).toBe('#E6A23C')
    expect(rects[1].attributes('fill')).toBe('#F56C6C')
  })

  it('PARALLEL + result - 协调器节点按 finalResult 等级着色', () => {
    const wrapper = mount(OrchestrationPipeline, {
      props: {
        mode: 'PARALLEL',
        agentTypes: ['A', 'B'],
        result: {
          ...buildResult(),
          agentResults: {
            A: { agentType: 'A', alertLevel: 'YELLOW', score: 60, confidence: 0.5 },
            B: { agentType: 'B', alertLevel: 'RED', score: 90, confidence: 0.9 },
          },
          finalResult: { agentType: 'B', alertLevel: 'RED', score: 90, confidence: 0.9 },
        },
      },
    })
    // 协调器是最后一个节点
    const rects = wrapper.findAll('rect')
    const last = rects[rects.length - 1]
    expect(last.attributes('fill')).toBe('#F56C6C')
  })

  it('未在 result 中出现的 Agent 节点 - 显示 UNKNOWN 描边', () => {
    const wrapper = mount(OrchestrationPipeline, {
      props: {
        mode: 'SEQUENTIAL',
        agentTypes: ['A', 'B', 'C'],
        result: buildResult(),
      },
    })
    const rects = wrapper.findAll('rect')
    // 第三个 agent 不在 result.agentResults 中 -> UNKNOWN -> stroke 虚线
    const third = rects[2]
    expect(third.attributes('stroke')).toBe('#C0C4CC')
    expect(third.attributes('stroke-dasharray')).toBe('4 3')
  })

  it('无 result 时 - 所有节点 PENDING 描边', () => {
    const wrapper = mount(OrchestrationPipeline, {
      props: {
        mode: 'SEQUENTIAL',
        agentTypes: ['A', 'B'],
        result: null,
      },
    })
    const rects = wrapper.findAll('rect')
    // 第一个节点 stroke-dasharray 应为 '4 3'（PENDING 虚线）
    expect(rects[0].attributes('stroke-dasharray')).toBe('4 3')
  })
})
