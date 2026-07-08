/**
 * @file Agent 链路追踪 API
 * @description 对应后端 AgentTraceController (/agent/trace)
 * @module api/agent/trace
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import { request } from '@/utils/request'
import type { AgentTrace } from './types'

/** 按 traceId 查询完整链路 */
export const getByTraceId = (traceId: string) =>
  request<AgentTrace[]>({
    url: `/agent/trace/${traceId}`,
    method: 'GET',
  })

/** 按业务维度查询最近 trace */
export const recentByBiz = (bizType: string, bizId: string, limit = 50) =>
  request<AgentTrace[]>({
    url: '/agent/trace/recent',
    method: 'GET',
    params: { bizType, bizId, limit },
  })
