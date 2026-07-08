/**
 * @file Token 配额管理 API
 * @description 对应后端 TokenQuotaController (/agent/token-quota)
 * @module api/agent/token-quota
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import { request } from '@/utils/request'
import type { QuotaSummary } from './types'

/** 查询当月 Token 配额概览 */
export const getSummary = () =>
  request<QuotaSummary>({
    url: '/agent/token-quota/summary',
    method: 'GET',
  })

/** 重置当月 Token 配额 */
export const reset = () =>
  request<QuotaSummary>({
    url: '/agent/token-quota/reset',
    method: 'POST',
  })
