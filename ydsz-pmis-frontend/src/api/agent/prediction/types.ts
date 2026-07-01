/**
 * AI 智能体预测结果相关类型定义
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */

/** Agent 类型枚举 */
export type AgentTypeCode =
  | 'RISK_WARNING'
  | 'RESOURCE_RECOMMEND'
  | 'PROFIT_FORECAST'
  | 'WIN_RATE_PREDICT'
  | 'TIMESHEET_ANOMALY'

/** 告警等级 */
export type AlertLevel = 'NORMAL' | 'INFO' | 'YELLOW' | 'RED' | 'RECOMMEND'

/** 执行状态 */
export type RunStatus = 'RUNNING' | 'SUCCESS' | 'FAILED'

/** Agent 执行请求 */
export interface AgentRunRequest {
  agentType: AgentTypeCode
  bizType?: string
  bizId?: number
  bizRef?: string
  callerId?: number
  callerName?: string
  source?: string
  params?: Record<string, unknown>
}

/** Agent 预测结果 */
export interface AgentPrediction {
  id: number
  taskCode?: string
  agentType: AgentTypeCode
  bizType?: string
  bizId?: number
  bizRef?: string
  inputSnapshot?: string
  outputResult?: string
  alertLevel?: AlertLevel
  score?: number
  confidence?: number
  suggestion?: string
  matchedRules?: string[]
  costMs?: number
  modelVersion?: string
  status?: RunStatus
  errorMsg?: string
  callerId?: number
  callerName?: string
  source?: string
  createdAt?: string
  updatedAt?: string
}
