/**
 * @file 收入与利润快照管理 API
 * @description 提供项目执行阶段的收入确认记录管理及利润快照生成与查询能力，
 *              对应后端 RevenueController（/finance/revenue）与
 *              ProfitSnapshotController（/finance/profit/snapshot）。
 * @module api/finance/profit
 */
import { request } from '@/utils/request'
import type { RevenueVO, RevenueCreateDTO, ProfitSnapshotVO } from './types'

// ============= 收入确认 =============

/**
 * 分页查询收入确认记录
 * @param page 当前页码（从 1 开始）
 * @param size 每页条数
 * @param params 可选查询条件：关键字、立项 ID、确认方法
 * @returns 收入确认分页结果
 */
export const pageRevenues = (
  page: number,
  size: number,
  params?: { keyword?: string; initiationId?: number; method?: string },
) =>
  request<PageResult<RevenueVO>>({
    url: '/finance/revenue/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/**
 * 根据 ID 获取收入确认详情
 * @param id 收入确认记录 ID
 * @returns 收入确认详情
 */
export const getRevenue = (id: number) =>
  request<RevenueVO>({ url: `/finance/revenue/${id}`, method: 'GET' })

/**
 * 新建收入确认记录
 * @param data 收入确认创建参数
 * @returns 新建收入确认记录的 ID
 */
export const createRevenue = (data: RevenueCreateDTO) =>
  request<number>({ url: '/finance/revenue', method: 'POST', data })

/**
 * 根据 ID 删除收入确认记录
 * @param id 收入确认记录 ID
 * @returns 无返回值
 */
export const deleteRevenue = (id: number) =>
  request<void>({ url: `/finance/revenue/${id}`, method: 'DELETE' })

// ============= 利润快照 =============

/**
 * 分页查询利润快照列表
 * @param page 当前页码（从 1 开始）
 * @param size 每页条数
 * @param params 可选查询条件：立项 ID、周期（YYYY-MM）
 * @returns 利润快照分页结果
 */
export const pageProfitSnapshots = (
  page: number,
  size: number,
  params?: { initiationId?: number; period?: string },
) =>
  request<PageResult<ProfitSnapshotVO>>({
    url: '/finance/profit/snapshot/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/**
 * 根据 ID 获取利润快照详情
 * @param id 利润快照 ID
 * @returns 利润快照详情
 */
export const getProfitSnapshot = (id: number) =>
  request<ProfitSnapshotVO>({
    url: `/finance/profit/snapshot/${id}`,
    method: 'GET',
  })

/**
 * 触发指定立项与周期的利润快照生成
 * @param initiationId 立项 ID
 * @param period 周期（YYYY-MM）
 * @returns 新生成的利润快照 ID
 */
export const generateProfitSnapshot = (initiationId: number, period: string) =>
  request<number>({
    url: '/finance/profit/snapshot/generate',
    method: 'POST',
    params: { initiationId, period },
  })
