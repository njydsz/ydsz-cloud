/**
 * @file 对内职级成本费率 API
 * @description 提供对内职级成本费率（Internal Rate）相关的接口调用，
 *              包含分页查询、详情、新建、更新及删除等能力。
 *              对应后端 Controller：RateInternalController（/resource/rate-internal）。
 * @module api/resource/rate-internal
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { RateInternalVO, RateInternalCreateDTO } from './types'

export * from './types'

/**
 * 分页查询对内职级成本费率
 * @param page 当前页码（从 1 开始）
 * @param size 每页条数
 * @param params 可选过滤条件：levelCode 职级编码、departmentId 部门ID、status 状态
 * @returns 费率分页结果
 */
export const pageRateInternal = (
  page: number,
  size: number,
  params?: { levelCode?: string; departmentId?: number; status?: string },
) =>
  request<PageResult<RateInternalVO>>({
    url: '/resource/rate-internal/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/**
 * 查询对内职级成本费率详情
 * @param id 费率记录ID
 * @returns 费率详情
 */
export const getRateInternal = (id: number) =>
  request<RateInternalVO>({ url: `/resource/rate-internal/${id}`, method: 'GET' })

/**
 * 新建对内职级成本费率
 * @param data 费率创建 DTO
 * @returns 新建费率记录ID
 */
export const createRateInternal = (data: RateInternalCreateDTO) =>
  request<number>({ url: '/resource/rate-internal', method: 'POST', data })

/**
 * 更新对内职级成本费率
 * @param id 费率记录ID
 * @param data 费率创建 DTO
 * @returns 无返回值
 */
export const updateRateInternal = (id: number, data: RateInternalCreateDTO) =>
  request<void>({ url: `/resource/rate-internal/${id}`, method: 'PUT', data })

/**
 * 删除对内职级成本费率
 * @param id 费率记录ID
 * @returns 无返回值
 */
export const deleteRateInternal = (id: number) =>
  request<void>({ url: `/resource/rate-internal/${id}`, method: 'DELETE' })
