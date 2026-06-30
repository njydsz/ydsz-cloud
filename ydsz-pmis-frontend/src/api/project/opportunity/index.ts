import { request } from '@/utils/request'
import type {
  OpportunityVO,
  OpportunityCreateDTO,
  OpportunityUpdateDTO,
  OpportunityStatusDTO,
} from './types'

/** 分页 */
export const pageOpportunities = (
  page: number,
  size: number,
  params?: { keyword?: string; status?: string; level?: string; ownerId?: number },
) =>
  request<PageResult<OpportunityVO>>({
    url: '/project/opportunity/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/** 详情 */
export const getOpportunity = (id: number) =>
  request<OpportunityVO>({ url: `/project/opportunity/${id}`, method: 'GET' })

/** 创建 */
export const createOpportunity = (data: OpportunityCreateDTO) =>
  request<number>({ url: '/project/opportunity', method: 'POST', data })

/** 更新 */
export const updateOpportunity = (data: OpportunityUpdateDTO) =>
  request<void>({ url: '/project/opportunity', method: 'PUT', data })

/** 变更状态 */
export const changeOpportunityStatus = (data: OpportunityStatusDTO) =>
  request<void>({ url: '/project/opportunity/status', method: 'PUT', data })

/** 删除 */
export const deleteOpportunity = (id: number) =>
  request<void>({ url: `/project/opportunity/${id}`, method: 'DELETE' })

/** 评估赢率 */
export const evaluateWinRate = (id: number, customerCredit?: string, hasHistory = false) =>
  request<number>({
    url: `/project/opportunity/${id}/evaluate-winrate`,
    method: 'POST',
    params: { customerCredit, hasHistory },
  })

/** 转立项 */
export const convertToInitiation = (id: number, sponsorId?: number, pmId?: number) =>
  request<number>({
    url: `/project/opportunity/${id}/convert-to-initiation`,
    method: 'POST',
    params: { sponsorId, pmId },
  })

/** 按状态聚合 */
export const aggregateOpportunityByStatus = (tenantId?: number) =>
  request<Array<Record<string, unknown>>>({
    url: '/project/opportunity/aggregate/status',
    method: 'GET',
    params: { tenantId },
  })

/** 按分级聚合 */
export const aggregateOpportunityByLevel = (tenantId?: number) =>
  request<Array<Record<string, unknown>>>({
    url: '/project/opportunity/aggregate/level',
    method: 'GET',
    params: { tenantId },
  })
