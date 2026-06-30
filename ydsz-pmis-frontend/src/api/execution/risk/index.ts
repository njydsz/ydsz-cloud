import { request } from '@/utils/request'
import type { RiskVO, RiskCreateDTO, RiskStatusDTO } from './types'

export const pageRisks = (
  page: number,
  size: number,
  params?: { keyword?: string; status?: string; level?: string; initiationId?: number },
) =>
  request<PageResult<RiskVO>>({
    url: '/execution/risk/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

export const getRisk = (id: number) =>
  request<RiskVO>({ url: `/execution/risk/${id}`, method: 'GET' })

export const createRisk = (data: RiskCreateDTO) =>
  request<number>({ url: '/execution/risk', method: 'POST', data })

export const changeRiskStatus = (data: RiskStatusDTO) =>
  request<void>({ url: '/execution/risk/status', method: 'PUT', data })

export const deleteRisk = (id: number) =>
  request<void>({ url: `/execution/risk/${id}`, method: 'DELETE' })
