import { request } from '@/utils/request'
import type { DailyReconcileVO, DailyReconcileAggregateVO } from './types'

/** 触发一次对账（默认对账日期 = 前一天） */
export const runDailyReconcile = (date?: string) =>
  request<number>({
    url: '/execution/daily-reconcile/run',
    method: 'POST',
    params: { date },
  })

/** 按日期范围 + 状态查询对账记录 */
export const queryReconcileByDateRange = (params: {
  from?: string
  to?: string
  status?: string
}) =>
  request<DailyReconcileVO[]>({
    url: '/execution/daily-reconcile/query',
    method: 'GET',
    params,
  })

/** 按状态聚合统计对账结果 */
export const aggregateReconcileStatus = (params: { from?: string; to?: string }) =>
  request<DailyReconcileAggregateVO[]>({
    url: '/execution/daily-reconcile/aggregate',
    method: 'GET',
    params,
  })

/** 手动触发每日对账（兼容 alert 重试） */
export const retryReconcile = (date: string) =>
  request<number>({
    url: '/execution/daily-reconcile/run',
    method: 'POST',
    params: { date },
  })
