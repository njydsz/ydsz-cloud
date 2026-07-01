/**
 * 多智能体编排 API
 *
 * 与后端 com.njydsz.pmis.agent.controller.AgentOrchestrationController 对齐。
 * 支持 4 种编排模式：SEQUENTIAL / PARALLEL / VOTING / CASCADE。
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

/** 协调多 Agent 编排执行 */
export const coordinate = (payload: OrchestrationRequest) =>
  request<OrchestrationResult>({
    url: '/agent/orchestration/coordinate',
    method: 'POST',
    data: payload,
  })

/** 拉取已注册的 Agent 列表（用于编排页面下拉选择） */
export const listAgents = () =>
  request<AgentTypeInfo[]>({
    url: '/agent/orchestration/agents',
    method: 'GET',
  })

/** 拉取支持的编排模式（用于模式切换下拉） */
export const listModes = () =>
  request<OrchestrationMode[]>({
    url: '/agent/orchestration/modes',
    method: 'GET',
  })
