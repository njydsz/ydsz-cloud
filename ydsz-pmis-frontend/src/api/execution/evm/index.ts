/**
 * @file EVM 挣值管理 API
 * @description 提供项目挣值管理（Earned Value Management）相关的接口调用，
 *              包含测量记录的分页查询、详情、录入/更新、删除、按项目查询及健康仪表盘等能力。
 *              对应后端 Controller：EvmController（/execution/evm）。
 * @module api/execution/evm
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type {
  EvmMeasureVO,
  EvmMeasureCreateDTO,
  EvmDashboardVO,
} from './types'

export * from './types'

/**
 * 分页查询 EVM 测量记录
 * @param page 当前页码（从 1 开始）
 * @param size 每页条数
 * @param params 可选过滤条件：initiationId 立项ID、alertLevel 预警等级（GREEN/YELLOW/RED）
 * @returns EVM 测量记录分页结果
 */
export const pageEvm = (
  page: number,
  size: number,
  params?: { initiationId?: number; alertLevel?: string },
) =>
  request<PageResult<EvmMeasureVO>>({
    url: '/api/project/execution/evm/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/**
 * 查询 EVM 测量记录详情
 * @param id 测量记录ID
 * @returns EVM 测量记录详情
 */
export const getEvm = (id: number) =>
  request<EvmMeasureVO>({ url: `/api/project/execution/evm/${id}`, method: 'GET' })

/**
 * 录入 / 更新（按 initiation+wbs+period 幂等）
 * @param data EVM 测量记录创建 DTO
 * @returns 新建或更新后的记录ID
 */
export const saveEvm = (data: EvmMeasureCreateDTO) =>
  request<number>({ url: '/api/project/execution/evm', method: 'POST', data })

/**
 * 删除 EVM 测量记录
 * @param id 测量记录ID
 * @returns 无返回值
 */
export const deleteEvm = (id: number) =>
  request<void>({ url: `/api/project/execution/evm/${id}`, method: 'DELETE' })

/**
 * 按项目查询 EVM 测量记录列表
 * @param initiationId 立项ID
 * @returns 该立项下所有 EVM 测量记录
 */
export const listEvmByInitiation = (initiationId: number) =>
  request<EvmMeasureVO[]>({
    url: '/api/project/execution/evm/by-initiation',
    method: 'GET',
    params: { initiationId },
  })

/**
 * 项目 EVM 健康仪表盘（汇总：CPI/SPI 平均、EAC/VAC、趋势、预警条数）
 * @param initiationId 立项ID
 * @returns 项目 EVM 仪表盘聚合数据
 */
export const getEvmDashboard = (initiationId: number) =>
  request<EvmDashboardVO>({
    url: '/api/project/execution/evm/dashboard',
    method: 'GET',
    params: { initiationId },
  })
