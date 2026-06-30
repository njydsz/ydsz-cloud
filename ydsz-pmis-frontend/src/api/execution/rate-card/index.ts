/**
 * 对外报价费率 Rate Card API
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { RateCardVO, RateCardCreateDTO } from './types'

export * from './types'

/** 分页 */
export const pageRateCards = (
  page: number,
  size: number,
  params?: { levelCode?: string; status?: string },
) =>
  request<PageResult<RateCardVO>>({
    url: '/execution/rate-card/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/** 详情 */
export const getRateCard = (id: number) =>
  request<RateCardVO>({ url: `/execution/rate-card/${id}`, method: 'GET' })

/** 新建 */
export const createRateCard = (data: RateCardCreateDTO) =>
  request<number>({ url: '/execution/rate-card', method: 'POST', data })

/** 更新 */
export const updateRateCard = (id: number, data: RateCardCreateDTO) =>
  request<void>({ url: `/execution/rate-card/${id}`, method: 'PUT', data })

/** 删除 */
export const deleteRateCard = (id: number) =>
  request<void>({ url: `/execution/rate-card/${id}`, method: 'DELETE' })

/** 按职级 */
export const listRateCardByLevel = (levelCode: string) =>
  request<RateCardVO[]>({
    url: '/execution/rate-card/by-level',
    method: 'GET',
    params: { levelCode },
  })

/** 命中有效费率 */
export const matchRateCard = (params: {
  levelCode: string
  projectType?: string
  customerLevel?: string
  date?: string
}) =>
  request<RateCardVO>({
    url: '/execution/rate-card/match',
    method: 'GET',
    params,
  })
