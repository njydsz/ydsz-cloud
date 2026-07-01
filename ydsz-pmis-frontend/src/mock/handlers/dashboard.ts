/**
 * @file 首页仪表盘 Mock 数据处理器
 * @description 为首页 Cockpit 的 KPI 概览、EVM 健康度、告警 TOP N、告警汇总、KPI 趋势等 API 路径提供 Mock 数据
 * @module mock/handlers/dashboard
 */
import type { MockHandler } from './types'

/**
 * 首页仪表盘 Mock 处理器集合
 * @returns {MockHandler[]} 覆盖概览 KPI、EVM 健康度、TOP N 预警、告警汇总、KPI 趋势等接口的 Mock 处理器
 */
export const dashboardHandlers: MockHandler[] = [
  // ===== 概览 KPI：活跃项目、收入、毛利、EVM 分布、利用率、闲置成本、健康度分布等 =====
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
  // ===== EVM 健康度分布（红 / 黄 / 绿项目数） =====
  {
    method: 'GET',
    path: '/execution/cockpit/evm-health',
    handler: () => ({ RED: 2, YELLOW: 5, NORMAL: 18 }),
  },
  // ===== 预警 TOP N：项目维度告警等级与告警次数 =====
  {
    method: 'GET',
    path: '/execution/alert/cockpit/topn',
    handler: () => [
      { projectCode: 'P001', projectName: '项目甲', alertLevel: 'RED', alertCount: 8 },
      { projectCode: 'P002', projectName: '项目乙', alertLevel: 'YELLOW', alertCount: 5 },
      { projectCode: 'P003', projectName: '项目丙', alertLevel: 'RED', alertCount: 3 },
    ],
  },
  // ===== 告警汇总：红黄告警数、总告警数、事件列表与头条事件 =====
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
  // ===== KPI 趋势：合同额 / 确认收入 / 成本 / 毛利 / 毛利率多月序列 =====
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
