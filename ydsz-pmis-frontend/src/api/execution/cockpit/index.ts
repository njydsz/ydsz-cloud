import { request } from '@/utils/request'
import type {
  CockpitKpiVO,
  CockpitAlertSummaryVO,
  ExecutiveOverviewVO,
  KpiTrendVO,
  ProjectGroupKpiDTO,
  AlertEventDTO,
} from './types'

/** 重新导出常用类型，方便上层页面直接 import */
export type {
  CockpitKpiVO,
  CockpitAlertSummaryVO,
  ExecutiveOverviewVO,
  KpiTrendVO,
  ProjectGroupKpiDTO,
  AlertEventDTO,
}

/** 驾驶舱总览 KPI */
export const getCockpitOverview = (period?: string, drillDown?: Record<string, unknown>) =>
  request<CockpitKpiVO>({
    url: '/execution/cockpit/overview',
    method: 'GET',
    params: { period, ...(drillDown || {}) },
  })

/** EVM 健康度分布 */
export const getEvmHealthDistribution = (period?: string) =>
  request<Record<string, number>>({
    url: '/execution/cockpit/evm-health',
    method: 'GET',
    params: { period },
  })

/** Bench 闲置成本汇总 */
export const getBenchCostSummary = () =>
  request<Record<string, unknown>>({
    url: '/execution/cockpit/bench-cost',
    method: 'GET',
  })

/** 可计费利用率汇总 */
export const getUtilizationSummary = () =>
  request<Record<string, unknown>>({
    url: '/execution/cockpit/utilization',
    method: 'GET',
  })

/** 按事业部下钻 */
export const drillByDept = (period?: string) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/cockpit/drill/dept',
    method: 'GET',
    params: { period },
  })

/** 按项目类型下钻 */
export const drillByProjectType = (period?: string) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/cockpit/drill/project-type',
    method: 'GET',
    params: { period },
  })

/** 按客户下钻 */
export const drillByCustomer = (period?: string) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/cockpit/drill/customer',
    method: 'GET',
    params: { period },
  })

/** 合同总额年度趋势 */
export const getContractYearlyTrend = () =>
  request<Record<string, unknown>>({
    url: '/execution/cockpit/contract-yearly-trend',
    method: 'GET',
  })

// ========== 批次18 增量 ==========

/** 预警事件摘要（批次18） */
export const getAlertSummary = (period?: string) =>
  request<CockpitAlertSummaryVO>({
    url: '/execution/cockpit/alerts',
    method: 'GET',
    params: { period },
  })

/** 项目群驾驶舱（批次18） */
export const getProjectGroupOverview = (period?: string) =>
  request<Array<ProjectGroupKpiDTO>>({
    url: '/execution/cockpit/project-group',
    method: 'GET',
    params: { period },
  })

/** 高管看板（批次18） */
export const getExecutiveOverview = (period?: string) =>
  request<ExecutiveOverviewVO>({
    url: '/execution/cockpit/executive',
    method: 'GET',
    params: { period },
  })

/** KPI 趋势（批次18） */
export const getKpiTrend = (months = 12) =>
  request<KpiTrendVO>({
    url: '/execution/cockpit/kpi-trend',
    method: 'GET',
    params: { months },
  })
