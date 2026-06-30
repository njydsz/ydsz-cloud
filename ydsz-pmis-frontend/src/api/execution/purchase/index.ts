import { request } from '@/utils/request'
import type { PurchaseVO, PurchaseCreateDTO, ApprovalDTO } from './types'

export const pagePurchases = (
  page: number,
  size: number,
  params?: { keyword?: string; status?: string; initiationId?: number },
) =>
  request<PageResult<PurchaseVO>>({
    url: '/execution/purchase/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

export const getPurchase = (id: number) =>
  request<PurchaseVO>({ url: `/execution/purchase/${id}`, method: 'GET' })

export const createPurchase = (data: PurchaseCreateDTO) =>
  request<number>({ url: '/execution/purchase', method: 'POST', data })

export const changePurchaseStatus = (data: ApprovalDTO) =>
  request<void>({ url: '/execution/purchase/status', method: 'PUT', data })

export const deletePurchase = (id: number) =>
  request<void>({ url: `/execution/purchase/${id}`, method: 'DELETE' })
