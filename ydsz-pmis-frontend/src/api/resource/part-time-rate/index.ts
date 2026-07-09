/**
 * @file 兼职职级费率 API 接口封装
 * @description 兼职职级费率（PartTimeRate, P1-P18）相关接口，对应后端 PartTimeRateController（/part-time-rates）。
 * @module api/resource/part-time-rate
 */
import { request } from '@/utils/request'
import type { PartTimeRateVO, PartTimeRateCreateDTO, PartTimeRateUpdateDTO } from './types'

/** 分页查询兼职职级费率 */
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

/** 查询兼职职级费率详情 */
export const getPartTimeRate = (id: string) =>
  request<PartTimeRateVO>({ url: `/part-time-rates/${id}`, method: 'GET' })

/** 创建兼职职级费率 */
export const createPartTimeRate = (data: PartTimeRateCreateDTO) =>
  request<string>({ url: '/part-time-rates', method: 'POST', data })

/** 更新兼职职级费率 */
export const updatePartTimeRate = (id: string, data: PartTimeRateUpdateDTO) =>
  request<void>({ url: `/part-time-rates/${id}`, method: 'PUT', data })

/** 删除兼职职级费率 */
export const deletePartTimeRate = (id: string) =>
  request<void>({ url: `/part-time-rates/${id}`, method: 'DELETE' })

/** 按级别编码 + 日期匹配生效费率 */
export const matchPartTimeRate = (rateCode: string, date?: string) =>
  request<PartTimeRateVO>({ url: '/part-time-rates/match', method: 'GET', params: { rateCode, date } })

/** 查询某日期生效中的所有兼职费率 */
export const listEffectivePartTimeRates = (date?: string) =>
  request<PartTimeRateVO[]>({ url: '/part-time-rates/effective', method: 'GET', params: { date } })
