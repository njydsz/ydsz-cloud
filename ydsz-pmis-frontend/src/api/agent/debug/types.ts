/**
 * @file Agent 调试相关类型定义
 * @module api/agent/debug
 * @author ydsz-pmis-team
 * @since 1.0.0
 */

/** Agent 执行结果 */
export interface AgentResult {
  agentType?: string
  alertLevel?: string
  score?: number
  confidence?: number
  suggestion?: string
  matchedRules?: string[]
  payload?: Record<string, unknown>
}

/** SSE 事件类型 */
export type SseEventType =
  | 'STEP_START'
  | 'THOUGHT'
  | 'ACTION'
  | 'OBSERVATION'
  | 'FINAL_ANSWER'
  | 'STEP_END'
  | 'DONE'
  | 'ERROR'

/** SSE 事件数据 */
export interface SseEvent {
  type: SseEventType
  data: string
  step?: number
  timestamp: number
}

/** ReAct 步骤记录 */
export interface ReActStep {
  step: number
  thought?: string
  action?: string
  actionInput?: string
  observation?: string
}

/** Agent 类型选项 */
export interface AgentTypeOption {
  code: string
  desc: string
}
