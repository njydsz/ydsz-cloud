/**
 * @file 经营驾驶舱 Mock 数据处理器
 * @description 为经营驾驶舱的项目群、高管 KPI、合同年度趋势、部门下钻等 API 路径提供 Mock 数据
 * @module mock/handlers/cockpit
 */
import type { MockHandler } from './types'

/**
 * 经营驾驶舱 Mock 处理器集合
 * @returns {MockHandler[]} 覆盖项目群概览、高管 KPI、合同年度趋势、部门下钻等接口的 Mock 处理器
 */
export const cockpitHandlers: MockHandler[] = [
  // ===== 项目群概览：各大区项目数、收入、健康度 =====
  {
    method: 'GET',
    path: '/api/project/report/cockpit/project-group',
    handler: () => ({
      groups: [
        { groupId: 1, groupName: '华东大区', projectCount: 12, revenue: 5000000, health: 'NORMAL' },
        { groupId: 2, groupName: '华南大区', projectCount: 8, revenue: 3200000, health: 'YELLOW' },
        { groupId: 3, groupName: '华北大区', projectCount: 5, revenue: 1800000, health: 'RED' },
      ],
    }),
  },
  // ===== 高管 KPI 概览：合同总额、收入、利润、毛利率、利用率及 TOP 项目 =====
  {
    method: 'GET',
    path: '/api/project/report/cockpit/executive',
    handler: () => ({
      kpis: {
        totalContract: 50000000,
        ytdRevenue: 38000000,
        ytdProfit: 9200000,
        avgMargin: 0.242,
        avgUtilization: 0.78,
      },
      topProjects: [
        { id: 1, name: '项目甲', margin: 0.35 },
        { id: 2, name: '项目乙', margin: 0.28 },
        { id: 3, name: '项目丙', margin: 0.24 },
      ],
    }),
  },
  // ===== 合同年度签订 / 回款趋势（多月序列） =====
  {
    method: 'GET',
    path: '/api/project/report/cockpit/contract-yearly-trend',
    handler: () => ({
      months: ['2026-01', '2026-02', '2026-03', '2026-04', '2026-05', '2026-06', '2026-07'],
      signedAmount: [1200, 1500, 1800, 1700, 2000, 2200, 2400],
      receivedAmount: [800, 1000, 1300, 1200, 1500, 1800, 2000],
    }),
  },
  // ===== 部门维度下钻：活跃项目数、收入、毛利 =====
  {
    method: 'GET',
    path: '/api/project/report/cockpit/drill/dept',
    handler: () => [
      { dimension: '部门A', activeProjectCount: 5, totalRevenue: 2000000, grossProfit: 600000 },
      { dimension: '部门B', activeProjectCount: 8, totalRevenue: 3000000, grossProfit: 900000 },
      { dimension: '部门C', activeProjectCount: 4, totalRevenue: 1500000, grossProfit: 450000 },
    ],
  },
]
