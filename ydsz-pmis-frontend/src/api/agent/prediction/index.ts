/**
 * @file AI 智能体预测结果 API 接口封装
 * @description 提供 Agent 同步/异步执行、内存执行、详情/分页/最近记录查询、
 *              按类型聚合与告警计数等接口；
 *              对应后端 com.njydsz.pmis.agent.controller.AgentController，
 *              结果持久化至 AgentPredictionDO（含 provider_trace_id 追踪链路）。
 * @module api/agent/prediction
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { AgentPrediction, AgentRunRequest } from './types'

/**
 * 同步执行 Agent
 * @param payload Agent 执行请求（含 agentType、业务上下文等）
 * @returns Agent 预测结果（含 score、alertLevel、suggestion 等）
 */
export const runAgent = (payload: AgentRunRequest) =>
  request<AgentPrediction>({
    url: '/agent/run',
    method: 'POST',
    data: payload,
  })

/**
 * 异步执行 Agent
 * @description 提交后立即返回，执行结果异步落库
 * @param payload Agent 执行请求
 * @returns 无返回值（void）
 */
export const runAgentAsync = (payload: AgentRunRequest) =>
  request<void>({
    url: '/agent/run-async',
    method: 'POST',
    data: payload,
  })

/**
 * 内存执行（不落库）
 * @description 用于即时探查，结果不持久化到 AgentPredictionDO
 * @param agentType Agent 类型编码
 * @param params 输入参数（由具体 Agent 解释）
 * @returns 执行结果（结构由 Agent 决定）
 */
export const inMemory = (agentType: string, params: Record<string, unknown>) =>
  request<unknown>({
    url: '/agent/in-memory',
    method: 'POST',
    params: { agentType },
    data: { params },
  })

/**
 * 查询 Agent 预测结果详情
 * @param id 预测记录主键 ID
 * @returns Agent 预测结果
 */
export const getById = (id: number) =>
  request<AgentPrediction>({
    url: `/agent/${id}`,
    method: 'GET',
  })

/**
 * 分页查询 Agent 预测记录
 * @param pageNo 页码（从 1 开始）
 * @param pageSize 每页条数
 * @param filter 过滤条件（agentType / alertLevel / status / bizType / bizId）
 * @returns 分页结果
 */
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

/**
 * 查询最近 Agent 预测记录
 * @param params 查询参数（agentType / alertLevel / limit）
 * @returns 最近预测记录列表
 */
export const recent = (params: { agentType?: string; alertLevel?: string; limit?: number } = {}) =>
  request<AgentPrediction[]>({
    url: '/agent/recent',
    method: 'GET',
    params,
  })

/**
 * 按类型/告警等级聚合统计
 * @param tenantId 租户 ID（可选）
 * @returns 聚合统计结果数组
 */
export const aggregateByType = (tenantId?: number) =>
  request<Array<Record<string, unknown>>>({
    url: '/agent/aggregate/type',
    method: 'GET',
    params: { tenantId },
  })

/**
 * 告警计数
 * @param params 过滤参数（alertLevel / agentType / tenantId）
 * @returns 符合条件的告警记录数
 */
export const countByAlertLevel = (params: { alertLevel?: string; agentType?: string; tenantId?: number } = {}) =>
  request<number>({
    url: '/agent/count',
    method: 'GET',
    params,
  })
