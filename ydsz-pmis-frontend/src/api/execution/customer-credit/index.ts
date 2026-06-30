import { request } from '@/utils/request'
import type { CustomerCreditVO, CreditAssessmentDTO } from './types'

export const pageCustomerCredits = (
  page: number,
  size: number,
  params?: { keyword?: string; level?: string; customerId?: number },
) =>
  request<PageResult<CustomerCreditVO>>({
    url: '/execution/customer-credit/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

export const getCustomerCredit = (id: number) =>
  request<CustomerCreditVO>({
    url: `/execution/customer-credit/${id}`,
    method: 'GET',
  })

export const assessCustomerCredit = (data: CreditAssessmentDTO) =>
  request<CustomerCreditVO>({
    url: '/execution/customer-credit/assess',
    method: 'POST',
    data,
  })

export const getCreditByCustomer = (customerId: number) =>
  request<CustomerCreditVO>({
    url: `/execution/customer-credit/by-customer/${customerId}`,
    method: 'GET',
  })
