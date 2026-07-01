/**
 * 混沌工程 mock (批次 24 P2-2)
 *
 * 模拟 ChaosController 接口, 维护一份内存中的实验表 + 注入历史。
 * 提供与真实后端完全一致的 URL/方法, 便于切到真实环境。
 */
import type { MockHandler } from './types'
import type { ChaosExperiment, ChaosEvent } from '@/api/chaos/types'

const experiments: Record<string, ChaosExperiment> = {
  'ContractService.getContract': {
    type: 'LATENCY',
    target: 'ContractService.getContract',
    latencyMs: 500,
    description: '合同查询接口注入 500ms 延迟',
    enabled: false,
    createdBy: 'admin',
  },
  'PaymentService.create': {
    type: 'EXCEPTION',
    target: 'PaymentService.create',
    exceptionClass: 'java.lang.RuntimeException',
    errorRate: 0.3,
    description: '付款创建注入 30% 异常率',
    enabled: true,
    createdBy: 'admin',
  },
  'ExecutionClient.fetchUtilization': {
    type: 'NETWORK_PARTITION',
    target: 'ExecutionClient.fetchUtilization',
    description: '模拟执行服务不可达, 验证 Feign Fallback',
    enabled: false,
    createdBy: 'admin',
  },
}

let historySeq: ChaosEvent[] = [
  { timestamp: Date.now() - 600_000, target: 'PaymentService.create', outcome: 'INJECTED', detail: '已注入 EXCEPTION' },
  { timestamp: Date.now() - 540_000, target: 'PaymentService.create', outcome: 'SKIPPED_PROBABILITY', detail: '未命中概率 0.85 > 0.3' },
  { timestamp: Date.now() - 480_000, target: 'ContractService.getContract', outcome: 'BLOCKED_BY_FLAG', detail: 'Feature flag 关闭' },
  { timestamp: Date.now() - 300_000, target: 'ExecutionClient.fetchUtilization', outcome: 'NOT_TRIGGERED', detail: '实验未启用' },
  { timestamp: Date.now() - 120_000, target: 'PaymentService.create', outcome: 'INJECTED', detail: '已注入 EXCEPTION' },
]

export const chaosHandlers: MockHandler[] = [
  {
    method: 'GET',
    path: '/chaos/experiments',
    handler: () => Object.values(experiments),
  },
  {
    method: 'GET',
    path: '/chaos/experiments/{target}',
    handler: ({ query }) => {
      const target = decodeURIComponent(query.target || '')
      return experiments[target] || null
    },
  },
  {
    method: 'POST',
    path: '/chaos/experiments',
    handler: ({ body }) => {
      const b = (body || {}) as ChaosExperiment
      if (!b.target) throw new Error('target 必填')
      experiments[b.target] = { ...b }
      return { success: true }
    },
  },
  {
    method: 'PUT',
    path: '/chaos/experiments/{target}',
    handler: ({ body, query }) => {
      const target = decodeURIComponent(query.target || '')
      const b = (body || {}) as ChaosExperiment
      experiments[target] = { ...b, target }
      return { success: true }
    },
  },
  {
    method: 'PUT',
    path: '/chaos/experiments/{target}/enabled',
    handler: ({ query }) => {
      const target = decodeURIComponent(query.target || '')
      const enabled = String(query.enabled) === 'true'
      if (!experiments[target]) throw new Error('实验不存在: ' + target)
      experiments[target].enabled = enabled
      return { success: true }
    },
  },
  {
    method: 'DELETE',
    path: '/chaos/experiments/{target}',
    handler: ({ query }) => {
      const target = decodeURIComponent(query.target || '')
      delete experiments[target]
      return { success: true }
    },
  },
  {
    method: 'GET',
    path: '/chaos/history',
    handler: () => historySeq.slice(-100).reverse(),
  },
  {
    method: 'POST',
    path: '/chaos/history/clear',
    handler: () => {
      historySeq = []
      return { success: true }
    },
  },
  {
    method: 'POST',
    path: '/chaos/dry-run',
    handler: ({ query }) => {
      const target = decodeURIComponent(query.target || '')
      const exp = experiments[target]
      if (!exp) {
        return { target, outcome: 'NOT_TRIGGERED', error: '实验不存在' }
      }
      if (!exp.enabled) {
        historySeq.unshift({ timestamp: Date.now(), target, outcome: 'NOT_TRIGGERED', detail: '实验未启用' })
        return { target, outcome: 'NOT_TRIGGERED', error: '' }
      }
      historySeq.unshift({ timestamp: Date.now(), target, outcome: 'INJECTED', detail: `已注入 ${exp.type}` })
      return { target, outcome: 'INJECTED', error: `Chaos injected (${exp.type})` }
    },
  },
]

/** 暴露给 chaos-dashboard 页面使用, 单元测试中重置状态 */
export function __resetChaosMock() {
  Object.keys(experiments).forEach((k) => delete experiments[k])
  historySeq = []
}
