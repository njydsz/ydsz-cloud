/**
 * @file 职级 API 接口封装
 * @description 职级（Rank）及其费率相关接口，对应后端 RankController（/ranks）。提供职级列表、生效费率查询、费率版本查询等能力。
 * @module api/resource/rank
 */
import { request } from '@/utils/request'
import type { RankVO, RankRateVO } from './types'

/**
 * 查询所有职级（L1-L18）
 * @returns 职级列表
 */
export const listRanks = () =>
  request<RankVO[]>({ url: '/ranks', method: 'GET' })

/**
 * 查询生效的职级费率
 * @param levelCode 职级编码（如 L5）
 * @param date 生效日期（可选，默认当前日期）
 * @returns 职级费率详情
 */
export const getRankRate = (levelCode: string, date?: string) =>
  request<RankRateVO>({ url: '/ranks/rate', method: 'GET', params: { levelCode, date } })

/**
 * 查询某职级所有费率版本
 * @param levelCode 职级编码
 * @returns 费率版本列表
 */
export const listRankRateVersions = (levelCode: string) =>
  request<RankRateVO[]>({ url: '/ranks/rate/versions', method: 'GET', params: { levelCode } })
