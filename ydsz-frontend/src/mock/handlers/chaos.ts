/**
 * @file 混沌工程 Mock 数据处理器
 * @description 模拟 ChaosController 接口，维护一份内存中的实验表 + 注入历史。
 *              提供与真实后端完全一致的 URL/方法，便于切到真实环境（批次 24 P2-2）。
 * @module mock/handlers/chaos
 */
import type { MockHandler } from './types'
import type { ChaosExperiment, ChaosEvent } from '@/api/chaos/types'

// 内存中的实验表：以 target（目标接口）为键
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

// 注入历史事件序列（按时间倒序维护，查询时取最近 100 条）
let historySeq: ChaosEvent[] = [
  { timestamp: Date.now() - 600_000, target: 'PaymentService.create', outcome: 'INJECTED', detail: '已注入 EXCEPTION' },
  { timestamp: Date.now() - 540_000, target: 'PaymentService.create', outcome: 'SKIPPED_PROBABILITY', detail: '未命中概率 0.85 > 0.3' },
  { timestamp: Date.now() - 480_000, target: 'ContractService.getContract', outcome: 'BLOCKED_BY_FLAG', detail: 'Feature flag 关闭' },
  { timestamp: Date.now() - 300_000, target: 'ExecutionClient.fetchUtilization', outcome: 'NOT_TRIGGERED', detail: '实验未启用' },
  { timestamp: Date.now() - 120_000, target: 'PaymentService.create', outcome: 'INJECTED', detail: '已注入 EXCEPTION' },
]

/**
 * 混沌工程 Mock 处理器集合
 * @returns {MockHandler[]} 覆盖实验查询/CRUD、启停、历史查询/清空、试运行等接口的 Mock 处理器
 */
export const chaosHandlers: MockHandler[] = [
  // ===== 查询全部实验列表 =====
  {
    method: 'GET',
    path: '/chaos/experiments',
    handler: () => Object.values(experiments),
  },
  // ===== 按 target 查询单个实验 =====
  {
    method: 'GET',
    path: '/chaos/experiments/{target}',
    handler: ({ query }) => {
      const target = decodeURIComponent(query.target || '')
      return experiments[target] || null
    },
  },
  // ===== 新建实验（target 必填，否则抛错） =====
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
  // ===== 更新实验（按 target 覆盖） =====
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
  // ===== 启用 / 禁用实验 =====
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
  // ===== 删除实验 =====
  {
    method: 'DELETE',
    path: '/chaos/experiments/{target}',
    handler: ({ query }) => {
      const target = decodeURIComponent(query.target || '')
      delete experiments[target]
      return { success: true }
    },
  },
  // ===== 查询注入历史（最近 100 条，倒序返回） =====
  {
    method: 'GET',
    path: '/chaos/history',
    handler: () => historySeq.slice(-100).reverse(),
  },
  // ===== 清空注入历史 =====
  {
    method: 'POST',
    path: '/chaos/history/clear',
    handler: () => {
      historySeq = []
      return { success: true }
    },
  },
  // ===== 试运行：按实验配置判定是否注入，并写入历史 =====
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

/**
 * 重置混沌 Mock 状态
 * @description 暴露给 chaos-dashboard 页面使用，单元测试中重置状态：清空实验表与注入历史
 * @returns {void}
 */
export function __resetChaosMock() {
  Object.keys(experiments).forEach((k) => delete experiments[k])
  historySeq = []
}
