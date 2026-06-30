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

/** 分页 */
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

/** 详情 */
export const getInitiation = (id: number) =>
  request<InitiationVO>({ url: `/project/initiation/${id}`, method: 'GET' })

/** 立项 */
export const createInitiation = (data: InitiationCreateDTO) =>
  request<number>({ url: '/project/initiation', method: 'POST', data })

/** 阶段迁移 */
export const changeInitiationStage = (data: InitiationStageDTO) =>
  request<void>({ url: '/project/initiation/stage', method: 'PUT', data })

/** 删除 */
export const deleteInitiation = (id: number) =>
  request<void>({ url: `/project/initiation/${id}`, method: 'DELETE' })

// ============= 预算 =============

/** 新增预算明细 */
export const addBudgetItem = (data: BudgetItemDTO) =>
  request<number>({ url: '/project/initiation/budget', method: 'POST', data })

/** 删除预算明细 */
export const deleteBudgetItem = (id: number) =>
  request<void>({ url: `/project/initiation/budget/${id}`, method: 'DELETE' })

/** 预算明细列表 */
export const listBudget = (initiationId: number) =>
  request<BudgetItemVO[]>({ url: `/project/initiation/${initiationId}/budget`, method: 'GET' })

/** 预算按分类汇总 */
export const sumBudgetByCategory = (initiationId: number) =>
  request<Array<Record<string, unknown>>>({
    url: `/project/initiation/${initiationId}/budget/summary`,
    method: 'GET',
  })

/** 重新汇总预算 */
export const recomputeBudget = (initiationId: number) =>
  request<number>({
    url: `/project/initiation/${initiationId}/budget/recompute`,
    method: 'POST',
  })

// ============= 门径 =============

/** 门径评审 */
export const reviewGate = (data: GateReviewDTO) =>
  request<number>({ url: '/project/initiation/gate/review', method: 'POST', data })

/** 门径评审记录 */
export const listGateReviews = (initiationId: number) =>
  request<GateReviewVO[]>({
    url: `/project/initiation/${initiationId}/gate/reviews`,
    method: 'GET',
  })

/** 启动审批流 */
export const startInitiationProcess = (id: number, initiatorId: number) =>
  request<string>({
    url: `/project/initiation/${id}/start-process`,
    method: 'POST',
    params: { initiatorId },
  })

/** 预算快照（供其他模块） */
export const getBudgetSnapshot = (id: number) =>
  request<Record<string, unknown>>({
    url: `/project/initiation/${id}/budget/snapshot`,
    method: 'GET',
  })
