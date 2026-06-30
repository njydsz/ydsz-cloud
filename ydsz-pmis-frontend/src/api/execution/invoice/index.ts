import { request } from '@/utils/request'
import type { InvoiceVO, InvoiceCreateDTO, InvoiceApprovalDTO } from './types'

export const pageInvoices = (
  page: number,
  size: number,
  params?: {
    keyword?: string
    status?: string
    invoiceType?: string
    customerId?: number
    initiationId?: number
  },
) =>
  request<PageResult<InvoiceVO>>({
    url: '/execution/invoice/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

export const getInvoice = (id: number) =>
  request<InvoiceVO>({ url: `/execution/invoice/${id}`, method: 'GET' })

export const createInvoice = (data: InvoiceCreateDTO) =>
  request<number>({ url: '/execution/invoice', method: 'POST', data })

export const approveInvoice = (data: InvoiceApprovalDTO) =>
  request<void>({ url: '/execution/invoice/approve', method: 'PUT', data })

export const issueInvoice = (data: InvoiceApprovalDTO) =>
  request<void>({ url: '/execution/invoice/issue', method: 'PUT', data })

export const reverseInvoice = (data: InvoiceApprovalDTO) =>
  request<void>({ url: '/execution/invoice/reverse', method: 'PUT', data })

export const deleteInvoice = (id: number) =>
  request<void>({ url: `/execution/invoice/${id}`, method: 'DELETE' })
