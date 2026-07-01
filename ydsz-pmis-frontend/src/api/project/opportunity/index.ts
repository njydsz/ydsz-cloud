/**
 * @file 商机管理 API 接口封装
 * @description 提供商机（Opportunity）模块的增删改查、状态变更、赢率评估、转立项及聚合统计等接口；
 *              对应后端 OpportunityController（/project/opportunity）。
 * @module api/project/opportunity
 */
import { request } from '@/utils/request'
import type {
  OpportunityVO,
  OpportunityCreateDTO,
  OpportunityUpdateDTO,
  OpportunityStatusDTO,
} from './types'

/**
 * 分页查询商机列表
 * @param page 当前页码（从 1 开始）
 * @param size 每页条数
 * @param params 查询条件：keyword 关键字、status 状态、level 分级、ownerId 责任人 ID
 * @returns 商机分页结果
 */
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

/**
 * 查询商机详情
 * @param id 商机 ID
 * @returns 商机详情对象
 */
export const getOpportunity = (id: number) =>
  request<OpportunityVO>({ url: `/project/opportunity/${id}`, method: 'GET' })

/**
 * 创建商机
 * @param data 商机创建数据
 * @returns 新建商机 ID
 */
export const createOpportunity = (data: OpportunityCreateDTO) =>
  request<number>({ url: '/project/opportunity', method: 'POST', data })

/**
 * 更新商机信息
 * @param data 商机更新数据（须包含 id）
 * @returns 无返回值
 */
export const updateOpportunity = (data: OpportunityUpdateDTO) =>
  request<void>({ url: '/project/opportunity', method: 'PUT', data })

/**
 * 变更商机状态
 * @param data 状态变更入参（包含 id、目标状态、失败原因等）
 * @returns 无返回值
 */
export const changeOpportunityStatus = (data: OpportunityStatusDTO) =>
  request<void>({ url: '/project/opportunity/status', method: 'PUT', data })

/**
 * 删除商机
 * @param id 商机 ID
 * @returns 无返回值
 */
export const deleteOpportunity = (id: number) =>
  request<void>({ url: `/project/opportunity/${id}`, method: 'DELETE' })

/**
 * 评估商机赢率
 * @param id 商机 ID
 * @param customerCredit 客户信用等级（可选）
 * @param hasHistory 是否存在历史合作记录，默认 false
 * @returns 评估出的赢率（百分比数值）
 */
export const evaluateWinRate = (id: number, customerCredit?: string, hasHistory = false) =>
  request<number>({
    url: `/project/opportunity/${id}/evaluate-winrate`,
    method: 'POST',
    params: { customerCredit, hasHistory },
  })

/**
 * 商机转立项
 * @param id 商机 ID
 * @param sponsorId 发起人 ID（可选）
 * @param pmId 项目经理 ID（可选）
 * @returns 新建立的立项记录 ID
 */
export const convertToInitiation = (id: number, sponsorId?: number, pmId?: number) =>
  request<number>({
    url: `/project/opportunity/${id}/convert-to-initiation`,
    method: 'POST',
    params: { sponsorId, pmId },
  })

/**
 * 按状态聚合商机统计
 * @param tenantId 租户 ID（可选）
 * @returns 按状态分组的聚合结果列表
 */
export const aggregateOpportunityByStatus = (tenantId?: number) =>
  request<Array<Record<string, unknown>>>({
    url: '/project/opportunity/aggregate/status',
    method: 'GET',
    params: { tenantId },
  })

/**
 * 按分级聚合商机统计
 * @param tenantId 租户 ID（可选）
 * @returns 按分级分组的聚合结果列表
 */
export const aggregateOpportunityByLevel = (tenantId?: number) =>
  request<Array<Record<string, unknown>>>({
    url: '/project/opportunity/aggregate/level',
    method: 'GET',
    params: { tenantId },
  })
