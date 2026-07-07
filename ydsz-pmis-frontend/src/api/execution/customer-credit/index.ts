/**
 * @file 客户信用 API 接口封装
 * @description 提供客户信用的分页查询、详情查询、信用评估、按客户查询信用等能力，
 *              对应后端 CustomerCreditController（/execution/credit）。
 * @module api/execution/credit
 */
import { request } from '@/utils/request'
import type { CustomerCreditVO, CreditAssessmentDTO } from './types'

/**
 * 分页查询客户信用
 * @param page 页码
 * @param size 每页大小
 * @param params 额外筛选条件（关键字、信用等级、客户 ID，可选）
 * @returns 客户信用分页结果
 */
export const pageCustomerCredits = (
  page: number,
  size: number,
  params?: { keyword?: string; level?: string; customerId?: number },
) =>
  request<PageResult<CustomerCreditVO>>({
    url: '/execution/credit/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/**
 * 查询客户信用详情
 * @param id 客户信用 ID
 * @returns 客户信用详情对象
 */
export const getCustomerCredit = (id: number) =>
  request<CustomerCreditVO>({
    url: `/execution/credit/${id}`,
    method: 'GET',
  })

/**
 * 客户信用评估
 * @param data 信用评估参数（客户 ID、合同数、合同总额、逾期次数等）
 * @returns 评估后的客户信用对象
 */
export const assessCustomerCredit = (data: CreditAssessmentDTO) =>
  request<CustomerCreditVO>({
    url: '/execution/credit/assess',
    method: 'POST',
    data,
  })

/**
 * 按客户 ID 查询信用
 * @param customerId 客户 ID
 * @returns 客户信用详情对象
 */
export const getCreditByCustomer = (customerId: number) =>
  request<CustomerCreditVO>({
    url: `/execution/credit/customer/${customerId}`,
    method: 'GET',
  })
