/**
 * 可计费利用率 API（驾驶舱 + 高级报表 + 独立页面）
 */
import { request } from '@/utils/request'

export * from './types'

/** 按区间聚合所有员工利用率明细（实时） */
export const aggregateUtilization = (from?: string, to?: string) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/billable-utilization/aggregate',
    method: 'GET',
    params: { from, to },
  })

/** 个人利用率 */
export const getPersonalUtilization = (employeeId: number, from?: string, to?: string) =>
  request<Record<string, unknown>>({
    url: '/execution/billable-utilization/personal',
    method: 'GET',
    params: { employeeId, from, to },
  })

/** 排行榜（按 utilizationPct 倒序） */
export const getUtilizationRank = (from?: string, to?: string, top = 20) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/billable-utilization/rank',
    method: 'GET',
    params: { from, to, top },
  })

/** 公司/团队整体均值（实时） */
export const getOverallUtilization = (from?: string, to?: string) =>
  request<Record<string, unknown>>({
    url: '/execution/billable-utilization/overall',
    method: 'GET',
    params: { from, to },
  })

/** 扫描预警员工 */
export const getUtilizationAlerts = (from?: string, to?: string) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/billable-utilization/alerts',
    method: 'GET',
    params: { from, to },
  })

/** 纯计算评估 */
export const evaluateUtilization = (totalHours: number, billableHours: number) =>
  request<Record<string, unknown>>({
    url: '/execution/billable-utilization/evaluate',
    method: 'GET',
    params: { totalHours, billableHours },
  })

/** 触发快照重算（运维手工 / 后台） */
export const recomputeUtilization = (period?: string, recomputeAll = false) =>
  request<Record<string, unknown>>({
    url: '/execution/billable-utilization/recompute',
    method: 'POST',
    params: { period, recomputeAll },
  })

/** 读取最新一期快照均值（驾驶舱取数） */
export const getSnapshotAverage = (period?: string) =>
  request<Record<string, unknown>>({
    url: '/execution/billable-utilization/snapshot-average',
    method: 'GET',
    params: { period },
  })
