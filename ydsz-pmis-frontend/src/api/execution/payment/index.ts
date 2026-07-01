/**
 * @file 回款管理 API 接口封装
 * @description 提供回款的分页查询、详情查询、创建、状态变更、核销分配、删除等能力，
 *              对应后端 PaymentController（/execution/payment）。
 * @module api/execution/payment
 */
import { request } from '@/utils/request'
import type { PaymentVO, PaymentCreateDTO, PaymentAllocationDTO } from './types'

/**
 * 分页查询回款
 * @param page 页码
 * @param size 每页大小
 * @param params 额外筛选条件（关键字、状态、客户 ID、立项 ID，可选）
 * @returns 回款分页结果
 */
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

/**
 * 查询回款详情
 * @param id 回款 ID
 * @returns 回款详情对象
 */
export const getPayment = (id: number) =>
  request<PaymentVO>({ url: `/execution/payment/${id}`, method: 'GET' })

/**
 * 创建回款
 * @param data 回款创建参数（编码、客户、立项、金额、付款方式等）
 * @returns 新建回款 ID
 */
export const createPayment = (data: PaymentCreateDTO) =>
  request<number>({ url: '/execution/payment', method: 'POST', data })

/**
 * 变更回款状态
 * @param id 回款 ID
 * @param targetStatus 目标状态
 * @param approverId 审批人 ID（可选）
 * @param approverName 审批人姓名（可选）
 * @returns 无返回值
 */
export const changePaymentStatus = (id: number, targetStatus: string, approverId?: number, approverName?: string) =>
  request<void>({
    url: `/execution/payment/${id}/status`,
    method: 'PUT',
    params: { targetStatus, approverId, approverName },
  })

/**
 * 回款核销分配（将回款分配到指定发票）
 * @param data 核销分配参数（回款 ID、发票 ID、金额）
 * @returns 无返回值
 */
export const allocatePayment = (data: PaymentAllocationDTO) =>
  request<void>({ url: '/execution/payment/allocate', method: 'POST', data })

/**
 * 删除回款
 * @param id 回款 ID
 * @returns 无返回值
 */
export const deletePayment = (id: number) =>
  request<void>({ url: `/execution/payment/${id}`, method: 'DELETE' })
