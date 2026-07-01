/**
 * @file 合同管理 API 接口封装
 * @description 提供合同（Contract）模块的主合同、合同模板、合同变更三大子模块的增删改查及状态迁移接口；
 *              对应后端 ContractController（/project/contract）及其子路径。
 * @module api/project/contract
 */
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

/**
 * 分页查询合同列表
 * @param page 当前页码（从 1 开始）
 * @param size 每页条数
 * @param params 查询条件：keyword 关键字、status 状态、customerId 客户 ID
 * @returns 合同分页结果
 */
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

/**
 * 查询合同详情
 * @param id 合同 ID
 * @returns 合同详情对象
 */
export const getContract = (id: number) =>
  request<ContractVO>({ url: `/project/contract/${id}`, method: 'GET' })

/**
 * 创建合同
 * @param data 合同创建数据
 * @returns 新建合同 ID
 */
export const createContract = (data: ContractCreateDTO) =>
  request<number>({ url: '/project/contract', method: 'POST', data })

/**
 * 更新合同信息
 * @param data 合同更新数据（须包含 id，其余字段可选）
 * @returns 无返回值
 */
export const updateContract = (data: Partial<ContractVO> & { id: number }) =>
  request<void>({ url: '/project/contract', method: 'PUT', data })

/**
 * 变更合同状态
 * @param data 状态变更入参（包含 id、目标状态、原因等）
 * @returns 无返回值
 */
export const changeContractStatus = (data: ContractStatusDTO) =>
  request<void>({ url: '/project/contract/status', method: 'PUT', data })

/**
 * 删除合同
 * @param id 合同 ID
 * @returns 无返回值
 */
export const deleteContract = (id: number) =>
  request<void>({ url: `/project/contract/${id}`, method: 'DELETE' })

// ============= 合同模板 =============

/**
 * 分页查询合同模板列表
 * @param page 当前页码（从 1 开始）
 * @param size 每页条数
 * @param params 查询条件：keyword 关键字、type 模板类型、status 状态
 * @returns 合同模板分页结果
 */
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

/**
 * 查询合同模板详情
 * @param id 合同模板 ID
 * @returns 合同模板详情对象
 */
export const getContractTemplate = (id: number) =>
  request<ContractTemplateVO>({
    url: `/project/contract/template/${id}`,
    method: 'GET',
  })

/**
 * 创建合同模板
 * @param data 合同模板创建数据
 * @returns 新建合同模板 ID
 */
export const createContractTemplate = (data: ContractTemplateCreateDTO) =>
  request<number>({
    url: '/project/contract/template',
    method: 'POST',
    data,
  })

/**
 * 变更合同模板状态（如发布/废弃）
 * @param data 状态变更入参（包含 id、目标状态）
 * @returns 无返回值
 */
export const changeContractTemplateStatus = (data: ContractTemplateStatusDTO) =>
  request<void>({
    url: '/project/contract/template/status',
    method: 'PUT',
    data,
  })

// ============= 合同变更 =============

/**
 * 分页查询合同变更列表
 * @param page 当前页码（从 1 开始）
 * @param size 每页条数
 * @param params 查询条件：contractId 合同 ID、status 状态、changeType 变更类型
 * @returns 合同变更分页结果
 */
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

/**
 * 创建合同变更申请
 * @param data 合同变更创建数据
 * @returns 新建合同变更记录 ID
 */
export const createContractChange = (data: ContractChangeCreateDTO) =>
  request<number>({
    url: '/project/contract/change',
    method: 'POST',
    data,
  })

/**
 * 变更合同变更申请的状态（提交/审核/通过/拒绝等）
 * @param data 状态变更入参（包含 id、目标状态、原因等）
 * @returns 无返回值
 */
export const changeContractChangeStatus = (data: ContractChangeStatusDTO) =>
  request<void>({
    url: '/project/contract/change/status',
    method: 'PUT',
    data,
  })
