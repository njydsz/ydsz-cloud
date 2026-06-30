import { request } from '@/utils/request'
import type {
  ContractVO,
  ContractCreateDTO,
  ContractStatusDTO,
  ContractTemplateVO,
  ContractTemplateCreateDTO,
  ContractTemplateStatusDTO,
  ContractChangeVO,
  ContractChangeCreateDTO,
  ContractChangeStatusDTO,
} from './types'

// ============= 主合同 =============
export const pageContracts = (
  page: number,
  size: number,
  params?: { keyword?: string; status?: string; customerId?: number },
) =>
  request<PageResult<ContractVO>>({
    url: '/project/contract/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

export const getContract = (id: number) =>
  request<ContractVO>({ url: `/project/contract/${id}`, method: 'GET' })

export const createContract = (data: ContractCreateDTO) =>
  request<number>({ url: '/project/contract', method: 'POST', data })

export const updateContract = (data: Partial<ContractVO> & { id: number }) =>
  request<void>({ url: '/project/contract', method: 'PUT', data })

export const changeContractStatus = (data: ContractStatusDTO) =>
  request<void>({ url: '/project/contract/status', method: 'PUT', data })

export const deleteContract = (id: number) =>
  request<void>({ url: `/project/contract/${id}`, method: 'DELETE' })

// ============= 合同模板 =============
export const pageContractTemplates = (
  page: number,
  size: number,
  params?: { keyword?: string; type?: string; status?: string },
) =>
  request<PageResult<ContractTemplateVO>>({
    url: '/project/contract/template/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

export const getContractTemplate = (id: number) =>
  request<ContractTemplateVO>({
    url: `/project/contract/template/${id}`,
    method: 'GET',
  })

export const createContractTemplate = (data: ContractTemplateCreateDTO) =>
  request<number>({
    url: '/project/contract/template',
    method: 'POST',
    data,
  })

export const changeContractTemplateStatus = (data: ContractTemplateStatusDTO) =>
  request<void>({
    url: '/project/contract/template/status',
    method: 'PUT',
    data,
  })

// ============= 合同变更 =============
export const pageContractChanges = (
  page: number,
  size: number,
  params?: { contractId?: number; status?: string; changeType?: string },
) =>
  request<PageResult<ContractChangeVO>>({
    url: '/project/contract/change/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

export const createContractChange = (data: ContractChangeCreateDTO) =>
  request<number>({
    url: '/project/contract/change',
    method: 'POST',
    data,
  })

export const changeContractChangeStatus = (data: ContractChangeStatusDTO) =>
  request<void>({
    url: '/project/contract/change/status',
    method: 'PUT',
    data,
  })
