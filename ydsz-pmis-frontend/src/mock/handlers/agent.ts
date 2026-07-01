/**
 * @file AI 智能体模块 Mock 数据处理器
 * @description 为 AI Agent 编排（orchestration）与预测（prediction）相关 API 路径提供 Mock 数据，
 *              支持批次 25 P1-6 E2E 流程联调
 * @module mock/handlers/agent
 */
import type { MockHandler } from './types'

/**
 * AI 智能体模块 Mock 处理器集合
 * @returns {MockHandler[]} 覆盖 Agent 类型查询、编排模式、协同执行、分页查询、单次运行等接口的 Mock 处理器
 */
export const agentHandlers: MockHandler[] = [
  // ===== 查询 Agent 类型列表（风险预警 / 利润预测 / EVM 异常 / 成本优化 / 资源推荐 / 赢率预测） =====
  {
    method: 'GET',
    path: '/agent/orchestration/agents',
    handler: () => [
      { agentType: 'RISK_WARNING', displayName: '风险预警' },
      { agentType: 'PROFIT_FORECAST', displayName: '利润预测' },
      { agentType: 'EVM_ALERT', displayName: 'EVM 异常' },
      { agentType: 'COST_OPTIMIZER', displayName: '成本优化' },
      { agentType: 'RESOURCE_RECOMMEND', displayName: '资源推荐' },
      { agentType: 'WIN_RATE_PREDICT', displayName: '赢率预测' },
    ],
  },
  // ===== 查询编排模式（顺序 / 并行 / 投票 / 级联） =====
  {
    method: 'GET',
    path: '/agent/orchestration/modes',
    handler: () => ['SEQUENTIAL', 'PARALLEL', 'VOTING', 'CASCADE'],
  },
  // ===== 多 Agent 协同执行：返回各 Agent 评分、告警等级及最终聚合等级 =====
  {
    method: 'POST',
    path: '/agent/orchestration/coordinate',
    handler: ({ body }) => {
      const b = (body || {}) as Record<string, unknown>
      return {
        success: true,
        mode: b.mode || 'SEQUENTIAL',
        results: [
          { agentType: 'RISK_WARNING', alertLevel: 'YELLOW', score: 0.42, suggestion: '建议关注成本' },
          { agentType: 'PROFIT_FORECAST', alertLevel: 'NORMAL', score: 0.78, suggestion: '利润健康' },
          { agentType: 'EVM_ALERT', alertLevel: 'NORMAL', score: 0.65, suggestion: 'EVM 在控' },
        ],
        finalLevel: 'YELLOW', // 最终聚合告警等级
        traceId: `orch-${Date.now()}`, // 编排链路追踪 ID
        cost: 142, // 本次编排 token 消耗（mock）
      }
    },
  },
  // ===== Agent 执行记录分页查询（含告警等级、评分、建议、业务对象） =====
  {
    method: 'GET',
    path: '/agent/page',
    handler: ({ query }) => {
      const list = Array.from({ length: Number(query.size || 10) }, (_, i) => ({
        id: i + 1,
        agentType: ['RISK_WARNING', 'PROFIT_FORECAST', 'EVM_ALERT'][i % 3],
        alertLevel: ['NORMAL', 'YELLOW', 'RED'][i % 3],
        score: 0.5 + (i % 5) * 0.1,
        suggestion: `AI 建议 ${i + 1}`,
        bizType: 'PROJECT',
        bizId: 100 + i,
        createdAt: '2026-07-01 10:00:00',
      }))
      return {
        list,
        total: 30,
        page: Number(query.page || 1),
        size: Number(query.size || 10),
        pages: 3,
      }
    },
  },
  // ===== 触发单次 Agent 运行：返回执行结果（含告警等级、评分、建议） =====
  {
    method: 'POST',
    path: '/agent/run',
    handler: () => ({
      id: 5001,
      agentType: 'RISK_WARNING',
      alertLevel: 'YELLOW',
      score: 0.55,
      suggestion: '当前 CPI=0.92, 需关注',
      bizType: 'PROJECT',
      bizId: 100,
      createdAt: new Date().toISOString(),
    }),
  },
]
