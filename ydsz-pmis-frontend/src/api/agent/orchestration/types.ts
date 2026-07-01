/**
 * 多智能体编排相关类型定义
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */

/** 4 种编排模式 */
export type OrchestrationModeCode = 'SEQUENTIAL' | 'PARALLEL' | 'VOTING' | 'CASCADE'

/** 编排模式枚举 */
export interface OrchestrationMode {
  code: OrchestrationModeCode
  desc: string
}

/** Agent 类型元信息 */
export interface AgentTypeInfo {
  code: string
  desc: string
  defaultAlertLevel?: string
}

/** 单个 Agent 的协调结果（trace 单条） */
export interface AgentTraceEntry {
  agentType: string
  mode?: OrchestrationModeCode | null
  score?: number | null
  confidence?: number | null
  note?: string | null
  ts: number
}

/** 单个 Agent 的输出 */
export interface AgentResultPayload {
  agentType?: string
  alertLevel?: string
  score?: number
  confidence?: number
  suggestion?: string
  matchedRules?: string[]
  payload?: Record<string, unknown>
}

/** 协调请求 */
export interface OrchestrationRequest {
  /** 关联业务类型：PROJECT / OPPORTUNITY / TIMESHEET / STAFF */
  bizType?: string
  /** 关联业务 ID */
  bizId?: number
  /** 关联业务编号/名称（冗余） */
  bizRef?: string
  /** 调用人 ID */
  callerId?: number
  /** 调用人姓名 */
  callerName?: string
  /** 来源：MANUAL / SCHEDULED / EVENT */
  source?: string
  /** 编排模式 */
  mode: OrchestrationModeCode
  /** 参与编排的 Agent 类型列表（按声明顺序） */
  agentTypes: string[]
  /** 输入事实上下文：cpi / spi / budgetConsumed 等 */
  facts?: Record<string, unknown>
  /** Agent 权重（VOTING 模式） */
  weights?: Record<string, number>
  /** CASCADE 模式置信度阈值（缺省 0.85） */
  confidenceThreshold?: number
  /** 备注 */
  remark?: string
}

/** 协调结果 */
export interface OrchestrationResult {
  /** 编排模式 */
  mode: OrchestrationModeCode
  /** 实际执行的 Agent 数量 */
  agentCount: number
  /** 总耗时 ms */
  totalCostMs: number
  /** 说明（如：第 2 个达标提前终止） */
  note?: string
  /** 实际执行的 Agent 列表（按执行顺序） */
  executedAgents: string[]
  /** 各 Agent 输出（key = agentType） */
  agentResults: Record<string, AgentResultPayload>
  /** 最终融合结果 */
  finalResult: AgentResultPayload
  /** 决策 trace */
  trace: AgentTraceEntry[]
}
