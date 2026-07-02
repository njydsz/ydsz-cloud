/**
 * @file Bench 闲置池 API 接口封装
 * @description Bench 闲置记录（BenchRecord）相关接口，对应后端 BenchRecordController（/bench）。提供分页、详情、入池/出池动作、当前记录查询、按池汇总、流动统计、闲置成本、仪表盘等能力。
 * @module api/resource/bench
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { BenchRecordVO, BenchRecordCreateDTO, BenchDashboardVO } from './types'

export * from './types'

/**
 * 分页查询 Bench 记录
 * @param page 页码
 * @param size 每页条数
 * @param params 可选筛选条件（poolId 资源池 ID、status 状态）
 * @returns 分页结果
 */
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

/**
 * 查询 Bench 记录详情
 * @param id Bench 记录 ID
 * @returns Bench 记录详情
 */
export const getBench = (id: number) =>
  request<BenchRecordVO>({ url: `/bench/${id}`, method: 'GET' })

/**
 * 入池 / 出池动作
 * @param data Bench 记录创建参数
 * @returns 处理记录 ID
 */
export const actBench = (data: BenchRecordCreateDTO) =>
  request<number>({ url: '/bench/act', method: 'POST', data })

/**
 * 查询员工当前生效的 Bench 记录
 * @param employeeId 员工 ID
 * @returns 当前 Bench 记录
 */
export const getActiveBench = (employeeId: number) =>
  request<BenchRecordVO>({ url: `/bench/active/${employeeId}`, method: 'GET' })

/**
 * 按资源池汇总 Bench 记录
 * @returns 按池汇总的统计列表
 */
export const aggregateByPool = () =>
  request<Array<Record<string, unknown>>>({
    url: '/bench/aggregate/by-pool',
    method: 'GET',
  })

/**
 * 查询时间区间内的 Bench 流动统计
 * @param from 起始日期（可选）
 * @param to 截止日期（可选）
 * @returns 流动统计列表
 */
export const flowByDateRange = (from?: string, to?: string) =>
  request<Array<Record<string, unknown>>>({
    url: '/bench/flow',
    method: 'GET',
    params: { from, to },
  })

/**
 * 查询累计闲置成本
 * @returns 累计闲置成本
 */
export const totalIdleCost = () =>
  request<number>({ url: '/bench/total-idle-cost', method: 'GET' })

/**
 * 查询 Bench 仪表盘数据
 * @returns 仪表盘聚合数据
 */
export const benchDashboard = () =>
  request<BenchDashboardVO>({ url: '/bench/dashboard', method: 'GET' })
