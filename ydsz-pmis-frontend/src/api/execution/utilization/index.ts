/**
 * @file 可计费利用率 API（驾驶舱 + 高级报表 + 独立页面）
 * @description 提供员工可计费利用率（Billable Utilization）相关的接口调用，
 *              覆盖聚合明细、个人利用率、排行榜、整体均值、预警扫描、纯计算评估、
 *              快照重算及最新一期快照均值查询等能力。
 *              对应后端 Controller：BillableUtilizationController（/execution/billable-utilization）。
 * @module api/execution/utilization
 */
import { request } from '@/utils/request'

export * from './types'

/**
 * 按区间聚合所有员工利用率明细（实时）
 * @param from 起始日期（YYYY-MM-DD），可选
 * @param to 结束日期（YYYY-MM-DD），可选
 * @returns 员工利用率明细数组
 */
export const aggregateUtilization = (from?: string, to?: string) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/billable-utilization/aggregate',
    method: 'GET',
    params: { from, to },
  })

/**
 * 查询个人利用率
 * @param employeeId 员工ID
 * @param from 起始日期（YYYY-MM-DD），可选
 * @param to 结束日期（YYYY-MM-DD），可选
 * @returns 个人利用率聚合数据
 */
export const getPersonalUtilization = (employeeId: number, from?: string, to?: string) =>
  request<Record<string, unknown>>({
    url: '/execution/billable-utilization/personal',
    method: 'GET',
    params: { employeeId, from, to },
  })

/**
 * 排行榜（按 utilizationPct 倒序）
 * @param from 起始日期（YYYY-MM-DD），可选
 * @param to 结束日期（YYYY-MM-DD），可选
 * @param top 返回前 N 条，默认 20
 * @returns 利用率排行榜数组
 */
export const getUtilizationRank = (from?: string, to?: string, top = 20) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/billable-utilization/rank',
    method: 'GET',
    params: { from, to, top },
  })

/**
 * 公司/团队整体均值（实时）
 * @param from 起始日期（YYYY-MM-DD），可选
 * @param to 结束日期（YYYY-MM-DD），可选
 * @returns 整体利用率聚合数据
 */
export const getOverallUtilization = (from?: string, to?: string) =>
  request<Record<string, unknown>>({
    url: '/execution/billable-utilization/overall',
    method: 'GET',
    params: { from, to },
  })

/**
 * 扫描预警员工
 * @param from 起始日期（YYYY-MM-DD），可选
 * @param to 结束日期（YYYY-MM-DD），可选
 * @returns 预警员工列表
 */
export const getUtilizationAlerts = (from?: string, to?: string) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/billable-utilization/alerts',
    method: 'GET',
    params: { from, to },
  })

/**
 * 纯计算评估（不落库，根据传入工时直接计算利用率）
 * @param totalHours 总工时
 * @param billableHours 可计费工时
 * @returns 利用率评估结果
 */
export const evaluateUtilization = (totalHours: number, billableHours: number) =>
  request<Record<string, unknown>>({
    url: '/execution/billable-utilization/evaluate',
    method: 'GET',
    params: { totalHours, billableHours },
  })

/**
 * 触发快照重算（运维手工 / 后台）
 * @param period 指定重算周期（YYYY-MM），可选
 * @param recomputeAll 是否重算所有周期，默认 false
 * @returns 重算任务执行结果
 */
export const recomputeUtilization = (period?: string, recomputeAll = false) =>
  request<Record<string, unknown>>({
    url: '/execution/billable-utilization/recompute',
    method: 'POST',
    params: { period, recomputeAll },
  })

/**
 * 读取最新一期快照均值（驾驶舱取数）
 * @param period 指定周期（YYYY-MM），可选；不传则取最新一期
 * @returns 快照均值数据
 */
export const getSnapshotAverage = (period?: string) =>
  request<Record<string, unknown>>({
    url: '/execution/billable-utilization/snapshot-average',
    method: 'GET',
    params: { period },
  })
