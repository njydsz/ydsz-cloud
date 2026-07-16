/**
 * @file 合同管理 API 接口封装
 * @description 提供合同的分页查询、详情查询、创建、更新、状态变更、删除等能力，
 *              对应后端 ContractController（/api/api/project/contract）。
 * @module api/contract
 */
import { request } from '@/utils/request'
import type { ContractVO, ContractCreateDTO, ContractStatusDTO } from './types'

/**
 * 分页查询合同列表
 * @param page 页码
 * @param size 每页大小
 * @param params 额外筛选条件（关键字、状态、客户 ID，可选）
 * @returns 合同分页结果
 */
export const pageContracts = (
  page: number,
  size: number,
  params?: {
    keyword?: string
    status?: string
    customerId?: number
  },
) =>
  request<PageResult<ContractVO>>({
    url: '/api/project/contract/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/**
 * 查询合同详情
 * @param id 合同 ID
 * @returns 合同详情对象
 */
export const getContract = (id: number) =>
  request<ContractVO>({ url: `/api/project/contract/${id}`, method: 'GET' })

/**
 * 创建合同
 * @param data 合同创建参数
 * @returns 新建合同 ID
 */
export const createContract = (data: ContractCreateDTO) =>
  request<number>({ url: '/api/project/contract', method: 'POST', data })

/**
 * 更新合同信息
 * @param data 合同更新参数（必须包含 id）
 * @returns 无返回值
 */
export const updateContract = (data: Partial<ContractVO> & { id: number }) =>
  request<void>({ url: '/api/project/contract', method: 'PUT', data })

/**
 * 变更合同状态
 * @param data 状态变更参数
 * @returns 无返回值
 */
export const changeContractStatus = (data: ContractStatusDTO) =>
  request<void>({ url: '/api/project/contract/status', method: 'PUT', data })

/**
 * 删除合同
 * @param id 合同 ID
 * @returns 无返回值
 */
export const deleteContract = (id: number) =>
  request<void>({ url: `/api/project/contract/${id}`, method: 'DELETE' })
