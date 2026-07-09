/**
 * @file 利润测算 API
 * @description 提供项目利润测算（Profit Simulation）相关的接口调用，
 *              包含分页查询、详情、新建、状态变更、删除及多版本对比等能力。
 *              对应后端 Controller：ProfitSimulationController（/finance/profit-simulation）。
 * @module api/finance/profit-simulation
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { ProfitSimulationVO, ProfitSimulationCreateDTO, SimulationStatusDTO } from './types'

export * from './types'

/**
 * 分页查询利润测算方案
 * @param page 当前页码（从 1 开始）
 * @param size 每页条数
 * @param params 可选过滤条件：initiationId 立项ID、scenarioType 场景类型、status 状态
 * @returns 利润测算方案分页结果
 */
export const pageProfitSimulations = (
  page: number,
  size: number,
  params?: { initiationId?: number; scenarioType?: string; status?: string },
) =>
  request<PageResult<ProfitSimulationVO>>({
    url: '/finance/profit-simulation/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/**
 * 查询利润测算方案详情
 * @param id 测算方案ID
 * @returns 测算方案详情
 */
export const getProfitSimulation = (id: number) =>
  request<ProfitSimulationVO>({
    url: `/finance/profit-simulation/${id}`,
    method: 'GET',
  })

/**
 * 新建利润测算方案
 * @param data 测算方案创建 DTO
 * @returns 新建测算方案ID
 */
export const createProfitSimulation = (data: ProfitSimulationCreateDTO) =>
  request<number>({ url: '/finance/profit-simulation', method: 'POST', data })

/**
 * 变更利润测算方案状态（提交/审批通过/驳回）
 * @param data 状态变更 DTO
 * @returns 无返回值
 */
export const changeSimulationStatus = (data: SimulationStatusDTO) =>
  request<void>({ url: '/finance/profit-simulation/status', method: 'PUT', data })

/**
 * 删除利润测算方案
 * @param id 测算方案ID
 * @returns 无返回值
 */
export const deleteProfitSimulation = (id: number) =>
  request<void>({ url: `/finance/profit-simulation/${id}`, method: 'DELETE' })

/**
 * 对比同立项下多个利润测算方案
 * @param initiationId 立项ID
 * @returns 多版本测算方案对比数据
 */
export const compareSimulations = (initiationId: number) =>
  request<Array<Record<string, unknown>>>({
    url: '/finance/profit-simulation/compare',
    method: 'GET',
    params: { initiationId },
  })
