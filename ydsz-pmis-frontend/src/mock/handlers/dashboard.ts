/**
 * 仪表盘 / Cockpit mock（KPI、健康度、趋势、TOP5）
 */
import type { MockHandler } from './types'

export const dashboardHandlers: MockHandler[] = [
  {
    method: 'GET',
    path: '/execution/cockpit/overview',
    handler: () => ({
      activeProjectCount: 25,
      totalRevenue: 12_000_000,
      recognizedRevenue: 8_500_000,
      totalGrossProfit: 2_800_000,
      grossMargin: 0.233,
      evmRedCount: 2,
      evmYellowCount: 5,
      evmGreenCount: 18,
      avgUtilization: 0.78,
      benchIdleCost: 120_000,
      normalProjects: 18,
      yellowProjects: 5,
      redProjects: 2,
    }),
  },
  {
    method: 'GET',
    path: '/execution/cockpit/evm-health',
    handler: () => ({ RED: 2, YELLOW: 5, NORMAL: 18 }),
  },
  {
    method: 'GET',
    path: '/execution/alert/cockpit/topn',
    handler: () => [
      { projectCode: 'P001', projectName: '项目甲', alertLevel: 'RED', alertCount: 8 },
      { projectCode: 'P002', projectName: '项目乙', alertLevel: 'YELLOW', alertCount: 5 },
      { projectCode: 'P003', projectName: '项目丙', alertLevel: 'RED', alertCount: 3 },
    ],
  },
  {
    method: 'GET',
    path: '/execution/cockpit/alert-summary',
    handler: () => ({
      redCount: 2,
      yellowCount: 5,
      totalCount: 7,
      events: [],
      topEvent: { title: 'EVM 红项目超限', description: '>3 个', severity: 'RED' },
    }),
  },
  {
    method: 'GET',
    path: '/execution/cockpit/kpi-trend',
    handler: () => ({
      periods: ['2026-02', '2026-03', '2026-04', '2026-05', '2026-06', '2026-07'],
      contractAmountSeries: [800, 950, 1100, 1050, 1150, 1200],
      confirmedRevenueSeries: [600, 720, 800, 750, 800, 850],
      totalCostSeries: [480, 580, 640, 600, 640, 680],
      grossProfitSeries: [120, 140, 160, 150, 160, 170],
      grossMarginPctSeries: [0.2, 0.19, 0.2, 0.2, 0.2, 0.2],
    }),
  },
]
