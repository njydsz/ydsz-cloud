/**
 * @file 多智能体编排 API 接口封装
 * @description 协调多 Agent 编排执行、拉取已注册 Agent 列表与支持的编排模式；
 *              对应后端 com.njydsz.pmis.agent.controller.AgentOrchestrationController，
 *              支持 SEQUENTIAL / PARALLEL / VOTING / CASCADE 四种编排模式（Blackboard + Strategy）。
 * @module api/agent/orchestration
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import { request } from '@/utils/request'
import type {
  OrchestrationRequest,
  OrchestrationResult,
  OrchestrationMode,
  AgentTypeInfo,
} from './types'

/**
 * 协调多 Agent 编排执行
 * @param payload 编排请求参数（含编排模式、参与 Agent 列表、事实上下文等）
 * @returns 编排融合结果（含各 Agent 输出、最终融合结果及决策 trace）
 */
export const coordinate = (payload: OrchestrationRequest) =>
  request<OrchestrationResult>({
    url: '/agent/orchestration/coordinate',
    method: 'POST',
    data: payload,
  })

/**
 * 拉取已注册的 Agent 列表
 * @description 用于编排页面下拉选择，返回各 Agent 类型的元信息
 * @returns Agent 类型元信息列表
 */
export const listAgents = () =>
  request<AgentTypeInfo[]>({
    url: '/agent/orchestration/agents',
    method: 'GET',
  })

/**
 * 拉取支持的编排模式
 * @description 用于模式切换下拉，返回 SEQUENTIAL/PARALLEL/VOTING/CASCADE 及其描述
 * @returns 编排模式枚举列表
 */
export const listModes = () =>
  request<OrchestrationMode[]>({
    url: '/agent/orchestration/modes',
    method: 'GET',
  })
