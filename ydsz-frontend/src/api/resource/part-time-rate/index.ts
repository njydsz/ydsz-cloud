/**
 * @file 兼职职级费率 API 接口封装
 * @description 兼职职级费率（PartTimeRate, P1-P18）相关接口，对应后端 PartTimeRateController（/part-time-rates）。
 * @module api/resource/part-time-rate
 */
import { request } from '@/utils/request'
import type { PartTimeRateVO, PartTimeRateCreateDTO, PartTimeRateUpdateDTO } from './types'

/**
 * 分页查询兼职职级费率
 * @param params 查询参数（页码、每页条数、关键字、级别段、状态）
 * @returns 兼职职级费率分页结果
 */
export const pagePartTimeRates = (params: {
  page?: number
  size?: number
  keyword?: string
  levelSegment?: string
  status?: string
}) => request<{ records: PartTimeRateVO[]; total: number; current: number; size: number }>({
  url: '/part-time-rates',
  method: 'GET',
  params,
})

/**
 * 查询兼职职级费率详情
 * @param id 费率 ID
 * @returns 兼职职级费率详情
 */
export const getPartTimeRate = (id: string) =>
  request<PartTimeRateVO>({ url: `/part-time-rates/${id}`, method: 'GET' })

/**
 * 创建兼职职级费率
 * @param data 费率创建参数
 * @returns 新建费率 ID
 */
export const createPartTimeRate = (data: PartTimeRateCreateDTO) =>
  request<string>({ url: '/part-time-rates', method: 'POST', data })

/**
 * 更新兼职职级费率
 * @param id 费率 ID
 * @param data 费率更新参数
 * @returns 无返回值
 */
export const updatePartTimeRate = (id: string, data: PartTimeRateUpdateDTO) =>
  request<void>({ url: `/part-time-rates/${id}`, method: 'PUT', data })

/**
 * 删除兼职职级费率
 * @param id 费率 ID
 * @returns 无返回值
 */
export const deletePartTimeRate = (id: string) =>
  request<void>({ url: `/part-time-rates/${id}`, method: 'DELETE' })

/**
 * 按级别编码 + 日期匹配生效费率
 * @param rateCode 级别编码（P1-P18）
 * @param date 生效日期（可选，默认当前日期）
 * @returns 匹配的生效费率
 */
export const matchPartTimeRate = (rateCode: string, date?: string) =>
  request<PartTimeRateVO>({ url: '/part-time-rates/match', method: 'GET', params: { rateCode, date } })

/**
 * 查询某日期生效中的所有兼职费率
 * @param date 生效日期（可选，默认当前日期）
 * @returns 生效费率列表
 */
export const listEffectivePartTimeRates = (date?: string) =>
  request<PartTimeRateVO[]>({ url: '/part-time-rates/effective', method: 'GET', params: { date } })
