/**
 * @file 发票管理 API 接口封装
 * @description 提供发票的分页查询、详情查询、创建、审批、开具、红冲、删除等能力，
 *              对应后端 InvoiceController（/execution/invoice）。
 * @module api/execution/invoice
 */
import { request } from '@/utils/request'
import type { InvoiceVO, InvoiceCreateDTO, InvoiceApprovalDTO } from './types'

/**
 * 分页查询发票
 * @param page 页码
 * @param size 每页大小
 * @param params 额外筛选条件（关键字、状态、发票类型、客户 ID、立项 ID，可选）
 * @returns 发票分页结果
 */
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

/**
 * 查询发票详情
 * @param id 发票 ID
 * @returns 发票详情对象
 */
export const getInvoice = (id: number) =>
  request<InvoiceVO>({ url: `/execution/invoice/${id}`, method: 'GET' })

/**
 * 创建发票
 * @param data 发票创建参数（编码、类型、开票依据、客户、金额等）
 * @returns 新建发票 ID
 */
export const createInvoice = (data: InvoiceCreateDTO) =>
  request<number>({ url: '/execution/invoice', method: 'POST', data })

/**
 * 审批发票
 * @param data 审批参数（发票 ID、审批人、原因等）
 * @returns 无返回值
 */
export const approveInvoice = (data: InvoiceApprovalDTO) =>
  request<void>({ url: '/execution/invoice/approve', method: 'PUT', data })

/**
 * 开具发票
 * @param data 开具参数（发票 ID、操作人等）
 * @returns 无返回值
 */
export const issueInvoice = (data: InvoiceApprovalDTO) =>
  request<void>({ url: '/execution/invoice/issue', method: 'PUT', data })

/**
 * 红冲发票
 * @param data 红冲参数（发票 ID、操作人、原因等）
 * @returns 无返回值
 */
export const reverseInvoice = (data: InvoiceApprovalDTO) =>
  request<void>({ url: '/execution/invoice/reverse', method: 'PUT', data })

/**
 * 删除发票
 * @param id 发票 ID
 * @returns 无返回值
 */
export const deleteInvoice = (id: number) =>
  request<void>({ url: `/execution/invoice/${id}`, method: 'DELETE' })
