/**
 * @file 报表模块 Mock 数据处理器
 * @description 为执行/报表中心的统计类接口提供 Mock 数据,
 *              覆盖利润、成本、回款台账、生命周期、利润汇总等报表端点。
 * @module mock/handlers/report
 */
import type { MockHandler } from './types'

/**
 * 报表模块 Mock 处理器集合
 *
 * 覆盖端点:
 * - GET /report/profit           利润概览
 * - GET /report/cost             成本结构占比
 * - GET /report/payment-ledger   回款台账
 * - GET /report/lifecycle        项目生命周期
 * - GET /report/profit-summary   利润汇总
 *
 * @returns 报表模块所有 Mock 处理器数组
 */
export const reportHandlers: MockHandler[] = [
  {
    method: 'GET',
    path: '/api/project/report/profit',
    handler: () => ({
      revenue: 8500000,
      laborCost: 3500000,
      purchaseCost: 1800000,
      expenseCost: 600000,
      totalCost: 5900000,
      grossProfit: 2600000,
      grossMargin: 0.306,
    }),
  },
  {
    method: 'GET',
    path: '/api/project/report/cost',
    handler: () => ({
      laborRatio: 0.6,
      purchaseRatio: 0.3,
      expenseRatio: 0.1,
    }),
  },
  {
    method: 'GET',
    path: '/api/project/report/payment-ledger',
    handler: () => ({
      total: 5000000,
      received: 3500000,
      pending: 1500000,
    }),
  },
  {
    method: 'GET',
    path: '/api/project/report/lifecycle',
    handler: () => ({
      stages: [
        { stage: '立项', date: '2026-01-15', amount: null },
        { stage: '合同', date: '2026-02-01', amount: 8000000 },
        { stage: '发票', date: '2026-03-15', amount: 4000000 },
        { stage: '回款', date: '2026-04-20', amount: 3500000 },
        { stage: '结项', date: '2026-06-30', amount: null },
      ],
    }),
  },
  {
    method: 'GET',
    path: '/api/project/report/profit-summary',
    handler: () =>
      Array.from({ length: 8 }, (_, i) => ({
        initiationId: i + 1,
        initiationName: `项目${i + 1}`,
        revenue: 1000000 + i * 200000,
        totalCost: 700000 + i * 150000,
        grossProfit: 300000 + i * 50000,
        grossMargin: 0.3,
      })),
  },
]
