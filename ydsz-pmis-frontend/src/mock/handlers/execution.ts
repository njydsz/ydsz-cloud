/**
 * 执行模块 mock (WBS/工时/采购/费用/风险/利润/EVM/费率/双费率/模拟)
 */
import type { MockHandler } from './types'

export const executionHandlers: MockHandler[] = [
  {
    method: 'GET',
    path: '/execution/wbs/page',
    handler: ({ query }) => ({
      list: Array.from({ length: Number(query.size || 10) }, (_, i) => ({
        id: i + 1,
        wbsCode: `WBS-${String(i + 1).padStart(4, '0')}`,
        wbsName: `WBS 任务 ${i + 1}`,
        parentId: i % 3 === 0 ? null : Math.floor(i / 3) + 1,
        progress: Math.floor((i * 13) % 100),
        assigneeName: `员工${(i % 5) + 1}`,
        status: ['PENDING', 'IN_PROGRESS', 'DONE'][i % 3],
      })),
      total: 200,
      page: Number(query.page || 1),
      size: Number(query.size || 10),
      pages: 20,
    }),
  },
  {
    method: 'GET',
    path: '/execution/evm/page',
    handler: ({ query }) => ({
      list: Array.from({ length: Number(query.size || 10) }, (_, i) => ({
        id: i + 1,
        period: `2026-${String((i % 12) + 1).padStart(2, '0')}`,
        cpi: 0.85 + (i % 5) * 0.05,
        spi: 0.9 + (i % 4) * 0.03,
        health: ['NORMAL', 'YELLOW', 'RED'][i % 3],
      })),
      total: 24,
      page: Number(query.page || 1),
      size: Number(query.size || 10),
      pages: 3,
    }),
  },
  {
    method: 'GET',
    path: '/execution/advanced-report/utilization-rank',
    handler: () =>
      Array.from({ length: 20 }, (_, i) => ({
        employeeId: i + 1,
        employeeName: `员工${i + 1}`,
        department: `部门${(i % 5) + 1}`,
        utilization: 60 + (i * 2) % 40,
      })),
  },
  {
    method: 'GET',
    path: '/execution/advanced-report/bench-cost',
    handler: () =>
      Array.from({ length: 12 }, (_, i) => ({
        period: `2026-${String(i + 1).padStart(2, '0')}`,
        totalCost: 50000 + i * 3000,
        headcount: 3 + (i % 4),
      })),
  },
  {
    method: 'GET',
    path: '/execution/advanced-report/risk-dashboard',
    handler: () =>
      Array.from({ length: 5 }, (_, i) => ({
        projectId: i + 1,
        projectName: `项目${i + 1}`,
        highRiskCount: i % 2,
        mediumRiskCount: (i + 1) % 3,
        lowRiskCount: (i + 2) % 4,
        highAlerts: i % 2,
        mediumAlerts: (i + 1) % 3,
        lowAlerts: (i + 2) % 4,
        alertCount: i + 3,
      })),
  },
]
