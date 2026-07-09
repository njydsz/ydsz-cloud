/**
 * @file 外包职级费率 API 接口封装
 * @description 外包职级费率（OutsourceRate, V1-V18）相关接口，对应后端 OutsourceRateController（/outsource-rates）。
 * @module api/resource/outsource-rate
 */
import { request } from '@/utils/request'
import type { OutsourceRateVO, OutsourceRateCreateDTO, OutsourceRateUpdateDTO } from './types'

/** 分页查询外包职级费率 */
export const pageOutsourceRates = (params: {
  page?: number
  size?: number
  keyword?: string
  levelSegment?: string
  status?: string
}) => request<{ records: OutsourceRateVO[]; total: number; current: number; size: number }>({
  url: '/outsource-rates',
  method: 'GET',
  params,
})

/** 查询外包职级费率详情 */
export const getOutsourceRate = (id: string) =>
  request<OutsourceRateVO>({ url: `/outsource-rates/${id}`, method: 'GET' })

/** 创建外包职级费率 */
export const createOutsourceRate = (data: OutsourceRateCreateDTO) =>
  request<string>({ url: '/outsource-rates', method: 'POST', data })

/** 更新外包职级费率 */
export const updateOutsourceRate = (id: string, data: OutsourceRateUpdateDTO) =>
  request<void>({ url: `/outsource-rates/${id}`, method: 'PUT', data })

/** 删除外包职级费率 */
export const deleteOutsourceRate = (id: string) =>
  request<void>({ url: `/outsource-rates/${id}`, method: 'DELETE' })

/** 按级别编码 + 日期匹配生效费率 */
export const matchOutsourceRate = (rateCode: string, date?: string) =>
  request<OutsourceRateVO>({ url: '/outsource-rates/match', method: 'GET', params: { rateCode, date } })

/** 查询某日期生效中的所有外包费率 */
export const listEffectiveOutsourceRates = (date?: string) =>
  request<OutsourceRateVO[]>({ url: '/outsource-rates/effective', method: 'GET', params: { date } })
