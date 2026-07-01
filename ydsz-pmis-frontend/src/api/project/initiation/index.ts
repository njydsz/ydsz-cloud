/**
 * @file 立项管理 API 接口封装
 * @description 提供立项（Initiation）模块的增删改查、阶段迁移、预算明细管理、门径评审及审批流启动等接口；
 *              对应后端 InitiationController（/project/initiation）。
 * @module api/project/initiation
 */
import { request } from '@/utils/request'
import type {
  InitiationVO,
  InitiationCreateDTO,
  InitiationStageDTO,
  BudgetItemDTO,
  BudgetItemVO,
  GateReviewDTO,
  GateReviewVO,
} from './types'

/**
 * 分页查询立项列表
 * @param page 当前页码（从 1 开始）
 * @param size 每页条数
 * @param params 查询条件：keyword 关键字、stage 阶段、projectLevel 项目分级、pmId 项目经理 ID
 * @returns 立项分页结果
 */
export const pageInitiations = (
  page: number,
  size: number,
  params?: { keyword?: string; stage?: string; projectLevel?: string; pmId?: number },
) =>
  request<PageResult<InitiationVO>>({
    url: '/project/initiation/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/**
 * 查询立项详情
 * @param id 立项 ID
 * @returns 立项详情对象
 */
export const getInitiation = (id: number) =>
  request<InitiationVO>({ url: `/project/initiation/${id}`, method: 'GET' })

/**
 * 创建立项
 * @param data 立项创建数据
 * @returns 新建立项 ID
 */
export const createInitiation = (data: InitiationCreateDTO) =>
  request<number>({ url: '/project/initiation', method: 'POST', data })

/**
 * 立项阶段迁移
 * @param data 阶段迁移入参（包含 id、目标阶段、门径代码）
 * @returns 无返回值
 */
export const changeInitiationStage = (data: InitiationStageDTO) =>
  request<void>({ url: '/project/initiation/stage', method: 'PUT', data })

/**
 * 删除立项
 * @param id 立项 ID
 * @returns 无返回值
 */
export const deleteInitiation = (id: number) =>
  request<void>({ url: `/project/initiation/${id}`, method: 'DELETE' })

// ============= 预算 =============

/**
 * 新增预算明细
 * @param data 预算明细数据
 * @returns 新建预算明细 ID
 */
export const addBudgetItem = (data: BudgetItemDTO) =>
  request<number>({ url: '/project/initiation/budget', method: 'POST', data })

/**
 * 删除预算明细
 * @param id 预算明细 ID
 * @returns 无返回值
 */
export const deleteBudgetItem = (id: number) =>
  request<void>({ url: `/project/initiation/budget/${id}`, method: 'DELETE' })

/**
 * 查询立项的预算明细列表
 * @param initiationId 立项 ID
 * @returns 预算明细列表
 */
export const listBudget = (initiationId: number) =>
  request<BudgetItemVO[]>({ url: `/project/initiation/${initiationId}/budget`, method: 'GET' })

/**
 * 预算按分类汇总
 * @param initiationId 立项 ID
 * @returns 按分类分组的汇总结果列表
 */
export const sumBudgetByCategory = (initiationId: number) =>
  request<Array<Record<string, unknown>>>({
    url: `/project/initiation/${initiationId}/budget/summary`,
    method: 'GET',
  })

/**
 * 重新汇总预算
 * @param initiationId 立项 ID
 * @returns 汇总后的预算总额
 */
export const recomputeBudget = (initiationId: number) =>
  request<number>({
    url: `/project/initiation/${initiationId}/budget/recompute`,
    method: 'POST',
  })

// ============= 门径 =============

/**
 * 门径评审
 * @param data 门径评审入参（包含立项 ID、门径代码、评审结果等）
 * @returns 评审记录 ID
 */
export const reviewGate = (data: GateReviewDTO) =>
  request<number>({ url: '/project/initiation/gate/review', method: 'POST', data })

/**
 * 查询门径评审记录列表
 * @param initiationId 立项 ID
 * @returns 门径评审记录列表
 */
export const listGateReviews = (initiationId: number) =>
  request<GateReviewVO[]>({
    url: `/project/initiation/${initiationId}/gate/reviews`,
    method: 'GET',
  })

/**
 * 启动立项审批流
 * @param id 立项 ID
 * @param initiatorId 发起人 ID
 * @returns 审批流程实例 ID
 */
export const startInitiationProcess = (id: number, initiatorId: number) =>
  request<string>({
    url: `/project/initiation/${id}/start-process`,
    method: 'POST',
    params: { initiatorId },
  })

/**
 * 获取预算快照（供其他模块使用）
 * @param id 立项 ID
 * @returns 预算快照数据
 */
export const getBudgetSnapshot = (id: number) =>
  request<Record<string, unknown>>({
    url: `/project/initiation/${id}/budget/snapshot`,
    method: 'GET',
  })
