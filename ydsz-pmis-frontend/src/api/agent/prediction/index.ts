/**
 * AI 智能体预测结果 API
 *
 * 与后端 com.njydsz.pmis.agent.controller.AgentController 对齐。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { AgentPrediction, AgentRunRequest } from './types'

/** 同步执行 Agent */
export const runAgent = (payload: AgentRunRequest) =>
  request<AgentPrediction>({
    url: '/agent/run',
    method: 'POST',
    data: payload,
  })

/** 异步执行 Agent */
export const runAgentAsync = (payload: AgentRunRequest) =>
  request<void>({
    url: '/agent/run-async',
    method: 'POST',
    data: payload,
  })

/** 内存执行（不落库） */
export const inMemory = (agentType: string, params: Record<string, unknown>) =>
  request<unknown>({
    url: '/agent/in-memory',
    method: 'POST',
    params: { agentType },
    data: { params },
  })

/** 详情 */
export const getById = (id: number) =>
  request<AgentPrediction>({
    url: `/agent/${id}`,
    method: 'GET',
  })

/** 分页 */
export const page = (
  pageNo: number,
  pageSize: number,
  filter: { agentType?: string; alertLevel?: string; status?: string; bizType?: string; bizId?: number } = {},
) =>
  request<PageResult<AgentPrediction>>({
    url: '/agent/page',
    method: 'GET',
    params: { page: pageNo, size: pageSize, ...filter },
  })

/** 最近记录 */
export const recent = (params: { agentType?: string; alertLevel?: string; limit?: number } = {}) =>
  request<AgentPrediction[]>({
    url: '/agent/recent',
    method: 'GET',
    params,
  })

/** 按类型/告警等级聚合 */
export const aggregateByType = (tenantId?: number) =>
  request<Array<Record<string, unknown>>>({
    url: '/agent/aggregate/type',
    method: 'GET',
    params: { tenantId },
  })

/** 告警计数 */
export const countByAlertLevel = (params: { alertLevel?: string; agentType?: string; tenantId?: number } = {}) =>
  request<number>({
    url: '/agent/count',
    method: 'GET',
    params,
  })
