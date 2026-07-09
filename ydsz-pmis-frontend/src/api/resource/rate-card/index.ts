/**
 * @file 对外报价费率 Rate Card API
 * @description 提供对外报价费率（Rate Card）相关的接口调用，
 *              包含分页查询、详情、新建、更新、删除、按职级查询及命中有效费率匹配等能力。
 *              对应后端 Controller：RateCardController（/resource/rate-card）。
 * @module api/resource/rate-card
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { RateCardVO, RateCardCreateDTO } from './types'

export * from './types'

/**
 * 分页查询对外报价费率
 * @param page 当前页码（从 1 开始）
 * @param size 每页条数
 * @param params 可选过滤条件：levelCode 职级编码、status 状态
 * @returns 费率分页结果
 */
export const pageRateCards = (
  page: number,
  size: number,
  params?: { levelCode?: string; status?: string },
) =>
  request<PageResult<RateCardVO>>({
    url: '/resource/rate-card/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/**
 * 查询对外报价费率详情
 * @param id 费率记录ID
 * @returns 费率详情
 */
export const getRateCard = (id: number) =>
  request<RateCardVO>({ url: `/resource/rate-card/${id}`, method: 'GET' })

/**
 * 新建对外报价费率
 * @param data 费率创建 DTO
 * @returns 新建费率记录ID
 */
export const createRateCard = (data: RateCardCreateDTO) =>
  request<number>({ url: '/resource/rate-card', method: 'POST', data })

/**
 * 更新对外报价费率
 * @param id 费率记录ID
 * @param data 费率创建 DTO
 * @returns 无返回值
 */
export const updateRateCard = (id: number, data: RateCardCreateDTO) =>
  request<void>({ url: `/resource/rate-card/${id}`, method: 'PUT', data })

/**
 * 删除对外报价费率
 * @param id 费率记录ID
 * @returns 无返回值
 */
export const deleteRateCard = (id: number) =>
  request<void>({ url: `/resource/rate-card/${id}`, method: 'DELETE' })

/**
 * 按职级查询对外报价费率列表
 * @param levelCode 职级编码
 * @returns 该职级下的费率列表
 */
export const listRateCardByLevel = (levelCode: string) =>
  request<RateCardVO[]>({
    url: '/resource/rate-card/by-level',
    method: 'GET',
    params: { levelCode },
  })

/**
 * 命中有效费率（按职级、项目类型、客户等级、日期匹配当前生效费率）
 * @param params 匹配条件：levelCode 职级、projectType 项目类型、customerLevel 客户等级、date 日期
 * @returns 命中的有效费率
 */
export const matchRateCard = (params: {
  levelCode: string
  projectType?: string
  customerLevel?: string
  date?: string
}) =>
  request<RateCardVO>({
    url: '/resource/rate-card/match',
    method: 'GET',
    params,
  })
