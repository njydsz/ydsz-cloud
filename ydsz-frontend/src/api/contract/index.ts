/**
 * @file 合同管理 API 接口封装
 * @description 提供合同的分页查询、详情查询、创建、更新、状态变更、删除等能力，
 *              对应后端 ContractController（/api/api/project/contract）。
 * @module api/contract
 */
import { request } from '@/utils/request'
import { createCrudApi } from '@/utils/crudApi'
import type { ContractVO, ContractStatusDTO } from './types'

export const contractApi = createCrudApi<ContractVO>('/api/project/contract')

// Re-export for backward compatibility
export const pageContracts = contractApi.page
export const getContract = contractApi.get
export const createContract = contractApi.create
export const updateContract = contractApi.update
export const deleteContract = contractApi.remove

/**
 * 变更合同状态
 * @param data 状态变更参数
 * @returns 无返回值
 */
export const changeContractStatus = (data: ContractStatusDTO) =>
  request<void>({ url: '/api/project/contract/status', method: 'PUT', data })
