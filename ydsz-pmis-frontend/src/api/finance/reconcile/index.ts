/**
 * @file 每日对账管理 API
 * @description 提供项目执行阶段的每日对账触发、按日期范围/状态查询、
 *              状态聚合统计及重试等能力，
 *              对应后端 DailyReconcileController（/finance/daily-reconcile）。
 * @module api/finance/reconcile
 */
import { request } from '@/utils/request'
import type { DailyReconcileVO, DailyReconcileAggregateVO } from './types'

/**
 * 触发一次对账（默认对账日期 = 前一天）
 * @param date 指定对账日期（YYYY-MM-DD，可选，不传则默认前一天）
 * @returns 本次对账处理记录数
 */
export const runDailyReconcile = (date?: string) =>
  request<number>({
    url: '/finance/daily-reconcile/run',
    method: 'POST',
    params: { date },
  })

/**
 * 按日期范围 + 状态查询对账记录
 * @param params 查询条件：起始日期、结束日期、状态
 * @returns 对账记录列表
 */
export const queryReconcileByDateRange = (params: {
  from?: string
  to?: string
  status?: string
}) =>
  request<DailyReconcileVO[]>({
    url: '/finance/daily-reconcile/query',
    method: 'GET',
    params,
  })

/**
 * 按状态聚合统计对账结果
 * @param params 查询条件：起始日期、结束日期
 * @returns 对账状态聚合统计列表
 */
export const aggregateReconcileStatus = (params: { from?: string; to?: string }) =>
  request<DailyReconcileAggregateVO[]>({
    url: '/finance/daily-reconcile/aggregate',
    method: 'GET',
    params,
  })

/**
 * 手动触发每日对账（兼容 alert 重试）
 * @param date 指定对账日期（YYYY-MM-DD）
 * @returns 本次对账处理记录数
 */
export const retryReconcile = (date: string) =>
  request<number>({
    url: '/finance/daily-reconcile/run',
    method: 'POST',
    params: { date },
  })
