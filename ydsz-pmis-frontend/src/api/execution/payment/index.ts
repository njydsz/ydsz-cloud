import { request } from '@/utils/request'
import type { PaymentVO, PaymentCreateDTO, PaymentAllocationDTO } from './types'

export const pagePayments = (
  page: number,
  size: number,
  params?: { keyword?: string; status?: string; customerId?: number; initiationId?: number },
) =>
  request<PageResult<PaymentVO>>({
    url: '/execution/payment/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

export const getPayment = (id: number) =>
  request<PaymentVO>({ url: `/execution/payment/${id}`, method: 'GET' })

export const createPayment = (data: PaymentCreateDTO) =>
  request<number>({ url: '/execution/payment', method: 'POST', data })

export const changePaymentStatus = (id: number, targetStatus: string, approverId?: number, approverName?: string) =>
  request<void>({
    url: `/execution/payment/${id}/status`,
    method: 'PUT',
    params: { targetStatus, approverId, approverName },
  })

export const allocatePayment = (data: PaymentAllocationDTO) =>
  request<void>({ url: '/execution/payment/allocate', method: 'POST', data })

export const deletePayment = (id: number) =>
  request<void>({ url: `/execution/payment/${id}`, method: 'DELETE' })
