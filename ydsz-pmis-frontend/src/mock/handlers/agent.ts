/**
 * AI 智能体模块 mock (orchestration / prediction)
 * 批次 25 P1-6 E2E 支持
 */
import type { MockHandler } from './types'

export const agentHandlers: MockHandler[] = [
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
  {
    method: 'GET',
    path: '/agent/orchestration/modes',
    handler: () => ['SEQUENTIAL', 'PARALLEL', 'VOTING', 'CASCADE'],
  },
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
        finalLevel: 'YELLOW',
        traceId: `orch-${Date.now()}`,
        cost: 142,
      }
    },
  },
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
