/**
 * 对内职级成本费率 API
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { RateInternalVO, RateInternalCreateDTO } from './types'

export * from './types'

export const pageRateInternal = (
  page: number,
  size: number,
  params?: { levelCode?: string; departmentId?: number; status?: string },
) =>
  request<PageResult<RateInternalVO>>({
    url: '/execution/rate-internal/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

export const getRateInternal = (id: number) =>
  request<RateInternalVO>({ url: `/execution/rate-internal/${id}`, method: 'GET' })

export const createRateInternal = (data: RateInternalCreateDTO) =>
  request<number>({ url: '/execution/rate-internal', method: 'POST', data })

export const updateRateInternal = (id: number, data: RateInternalCreateDTO) =>
  request<void>({ url: `/execution/rate-internal/${id}`, method: 'PUT', data })

export const deleteRateInternal = (id: number) =>
  request<void>({ url: `/execution/rate-internal/${id}`, method: 'DELETE' })
