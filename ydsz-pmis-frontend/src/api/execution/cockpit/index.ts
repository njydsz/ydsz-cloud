/**
 * @file 经营驾驶舱 API 接口封装
 * @description 提供驾驶舱总览 KPI、EVM 健康度分布、Bench 闲置成本、可计费利用率、
 *              多维度下钻、合同年度趋势、预警事件摘要、项目群驾驶舱、高管看板、
 *              KPI 趋势等能力，对应后端 CockpitController（/execution/cockpit）。
 * @module api/execution/cockpit
 */
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

/**
 * 查询驾驶舱总览 KPI
 * @param period 统计周期（可选）
 * @param drillDown 下钻维度参数（可选）
 * @returns 驾驶舱总览 KPI 对象
 */
export const getCockpitOverview = (period?: string, drillDown?: Record<string, unknown>) =>
  request<CockpitKpiVO>({
    url: '/execution/cockpit/overview',
    method: 'GET',
    params: { period, ...(drillDown || {}) },
  })

/**
 * 查询 EVM 健康度分布
 * @param period 统计周期（可选）
 * @returns 各健康度等级对应项目数（红/黄/绿）
 */
export const getEvmHealthDistribution = (period?: string) =>
  request<Record<string, number>>({
    url: '/execution/cockpit/evm-health',
    method: 'GET',
    params: { period },
  })

/**
 * 查询 Bench 闲置成本汇总
 * @returns Bench 闲置成本汇总对象
 */
export const getBenchCostSummary = () =>
  request<Record<string, unknown>>({
    url: '/execution/cockpit/bench-cost',
    method: 'GET',
  })

/**
 * 查询可计费利用率汇总
 * @returns 可计费利用率汇总对象
 */
export const getUtilizationSummary = () =>
  request<Record<string, unknown>>({
    url: '/execution/cockpit/utilization',
    method: 'GET',
  })

/**
 * 按事业部下钻
 * @param period 统计周期（可选）
 * @returns 事业部维度下钻统计列表
 */
export const drillByDept = (period?: string) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/cockpit/drill/dept',
    method: 'GET',
    params: { period },
  })

/**
 * 按项目类型下钻
 * @param period 统计周期（可选）
 * @returns 项目类型维度下钻统计列表
 */
export const drillByProjectType = (period?: string) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/cockpit/drill/project-type',
    method: 'GET',
    params: { period },
  })

/**
 * 按客户下钻
 * @param period 统计周期（可选）
 * @returns 客户维度下钻统计列表
 */
export const drillByCustomer = (period?: string) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/cockpit/drill/customer',
    method: 'GET',
    params: { period },
  })

/**
 * 查询合同总额年度趋势
 * @returns 合同总额年度趋势对象
 */
export const getContractYearlyTrend = () =>
  request<Record<string, unknown>>({
    url: '/execution/cockpit/contract-yearly-trend',
    method: 'GET',
  })

// ========== 批次18 增量 ==========

/**
 * 查询预警事件摘要（批次18）
 * @param period 统计周期（可选）
 * @returns 预警事件摘要对象
 */
export const getAlertSummary = (period?: string) =>
  request<CockpitAlertSummaryVO>({
    url: '/execution/cockpit/alerts',
    method: 'GET',
    params: { period },
  })

/**
 * 查询项目群驾驶舱 KPI（批次18）
 * @param period 统计周期（可选）
 * @returns 项目群 KPI 列表
 */
export const getProjectGroupOverview = (period?: string) =>
  request<Array<ProjectGroupKpiDTO>>({
    url: '/execution/cockpit/project-group',
    method: 'GET',
    params: { period },
  })

/**
 * 查询高管看板（批次18）
 * @param period 统计周期（可选）
 * @returns 高管看板对象
 */
export const getExecutiveOverview = (period?: string) =>
  request<ExecutiveOverviewVO>({
    url: '/execution/cockpit/executive',
    method: 'GET',
    params: { period },
  })

/**
 * 查询 KPI 趋势（批次18）
 * @param months 统计月份数（默认 12）
 * @returns KPI 趋势对象
 */
export const getKpiTrend = (months = 12) =>
  request<KpiTrendVO>({
    url: '/execution/cockpit/kpi-trend',
    method: 'GET',
    params: { months },
  })
