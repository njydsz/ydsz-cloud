/**
 * @file Agent 链路追踪类型定义
 * @module api/agent/trace
 * @author ydsz-pmis-team
 * @since 1.0.0
 */

/** Agent Trace Span DO */
export interface AgentTrace {
  id: string
  traceId: string
  spanId: string
  parentSpanId?: string
  agentType?: string
  bizType?: string
  bizId?: string
  bizRef?: string
  spanName: string
  stepIndex?: number
  status?: string
  inputData?: string
  outputData?: string
  errorMsg?: string
  costMs?: number
  providerTraceId?: string
  tenantId?: string
  createdAt: string
}

/** Span 名称枚举 */
export type SpanName =
  | 'AGENT_START'
  | 'STEP_START'
  | 'LLM_THOUGHT'
  | 'LLM_ACTION'
  | 'TOOL_OBSERVATION'
  | 'FINAL_ANSWER'
  | 'STEP_END'
  | 'AGENT_END'
  | 'AGENT_ERROR'
