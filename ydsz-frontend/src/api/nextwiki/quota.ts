/**
 * @file 存储配额 API
 * @description 提供用户/租户/项目维度的存储配额查询与设置能力，
 *              对应后端 QuotaController（/nextwiki/quota）。
 * @module api/nextwiki/quota
 */
import { request } from '@/utils/request'
import type { StorageQuotaVO, SetQuotaDTO } from './types'

/**
 * 查询当前用户/租户/项目的存储配额信息
 * @param scopeType 配额维度（user / tenant / project）
 * @param scopeId 维度 ID
 * @returns 配额信息
 */
export const getQuotaInfo = (scopeType?: string, scopeId?: string) =>
  request<StorageQuotaVO>({
    url: '/nextwiki/quota/info',
    method: 'GET',
    params: { scopeType, scopeId },
  })

/**
 * 设置存储配额（管理员操作）
 * @param data 配额设置参数
 */
export const setQuota = (data: SetQuotaDTO) =>
  request<void>({ url: '/nextwiki/quota/set', method: 'POST', data })
