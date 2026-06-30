/**
 * Bench 闲置池 API
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { BenchRecordVO, BenchRecordCreateDTO, BenchDashboardVO } from './types'

export * from './types'

export const pageBench = (
  page: number,
  size: number,
  params?: { poolId?: number; status?: string },
) =>
  request<PageResult<BenchRecordVO>>({
    url: '/bench/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

export const getBench = (id: number) =>
  request<BenchRecordVO>({ url: `/bench/${id}`, method: 'GET' })

/** 入池 / 出池 */
export const actBench = (data: BenchRecordCreateDTO) =>
  request<number>({ url: '/bench/act', method: 'POST', data })

/** 员工当前 Bench 记录 */
export const getActiveBench = (employeeId: number) =>
  request<BenchRecordVO>({ url: `/bench/active/${employeeId}`, method: 'GET' })

/** 按池汇总 */
export const aggregateByPool = () =>
  request<Array<Record<string, any>>>({
    url: '/bench/aggregate/by-pool',
    method: 'GET',
  })

/** 流动统计 */
export const flowByDateRange = (from?: string, to?: string) =>
  request<Array<Record<string, any>>>({
    url: '/bench/flow',
    method: 'GET',
    params: { from, to },
  })

/** 累计闲置成本 */
export const totalIdleCost = () =>
  request<number>({ url: '/bench/total-idle-cost', method: 'GET' })

/** Bench 仪表盘 */
export const benchDashboard = () =>
  request<BenchDashboardVO>({ url: '/bench/dashboard', method: 'GET' })
