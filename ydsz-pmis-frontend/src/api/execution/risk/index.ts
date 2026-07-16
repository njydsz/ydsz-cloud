/**
 * @file 风险管理 API
 * @description 提供项目执行阶段风险的增删改查及状态变更能力，
 *              对应后端 RiskController（/execution/risk）。
 * @module api/execution/risk
 */
import { request } from '@/utils/request'
import type { RiskVO, RiskCreateDTO, RiskStatusDTO } from './types'

/**
 * 分页查询风险列表
 * @param page 当前页码（从 1 开始）
 * @param size 每页条数
 * @param params 可选查询条件：关键字、状态、风险等级、立项 ID
 * @returns 风险分页结果
 */
export const pageRisks = (
  page: number,
  size: number,
  params?: { keyword?: string; status?: string; level?: string; initiationId?: number },
) =>
  request<PageResult<RiskVO>>({
    url: '/api/project/execution/risk/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/**
 * 根据 ID 获取风险详情
 * @param id 风险 ID
 * @returns 风险详情
 */
export const getRisk = (id: number) =>
  request<RiskVO>({ url: `/api/project/execution/risk/${id}`, method: 'GET' })

/**
 * 新建风险记录
 * @param data 风险创建参数
 * @returns 新建风险的 ID
 */
export const createRisk = (data: RiskCreateDTO) =>
  request<number>({ url: '/api/project/execution/risk', method: 'POST', data })

/**
 * 变更风险状态（打开/缓解中/关闭/接受）
 * @param data 状态变更参数
 * @returns 无返回值
 */
export const changeRiskStatus = (data: RiskStatusDTO) =>
  request<void>({ url: '/api/project/execution/risk/status', method: 'PUT', data })

/**
 * 根据 ID 删除风险记录
 * @param id 风险 ID
 * @returns 无返回值
 */
export const deleteRisk = (id: number) =>
  request<void>({ url: `/api/project/execution/risk/${id}`, method: 'DELETE' })
