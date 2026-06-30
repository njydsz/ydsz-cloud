import { request } from '@/utils/request'
import type { RevenueVO, RevenueCreateDTO, ProfitSnapshotVO } from './types'

// ============= 收入确认 =============
export const pageRevenues = (
  page: number,
  size: number,
  params?: { keyword?: string; initiationId?: number; method?: string },
) =>
  request<PageResult<RevenueVO>>({
    url: '/execution/revenue/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

export const getRevenue = (id: number) =>
  request<RevenueVO>({ url: `/execution/revenue/${id}`, method: 'GET' })

export const createRevenue = (data: RevenueCreateDTO) =>
  request<number>({ url: '/execution/revenue', method: 'POST', data })

export const deleteRevenue = (id: number) =>
  request<void>({ url: `/execution/revenue/${id}`, method: 'DELETE' })

// ============= 利润快照 =============
export const pageProfitSnapshots = (
  page: number,
  size: number,
  params?: { initiationId?: number; period?: string },
) =>
  request<PageResult<ProfitSnapshotVO>>({
    url: '/execution/profit/snapshot/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

export const getProfitSnapshot = (id: number) =>
  request<ProfitSnapshotVO>({
    url: `/execution/profit/snapshot/${id}`,
    method: 'GET',
  })

export const generateProfitSnapshot = (initiationId: number, period: string) =>
  request<number>({
    url: '/execution/profit/snapshot/generate',
    method: 'POST',
    params: { initiationId, period },
  })
