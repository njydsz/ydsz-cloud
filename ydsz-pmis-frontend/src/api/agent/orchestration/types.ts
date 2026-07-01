/**
 * @file 多智能体编排相关类型定义
 * @description 定义编排模式、Agent 类型元信息、单 Agent 输出、协调请求/响应等类型，
 *              供 orchestration/index.ts 及业务侧编排页面使用。
 * @module api/agent/orchestration
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */

/** 4 种编排模式：SEQUENTIAL(顺序) / PARALLEL(并行) / VOTING(投票) / CASCADE(级联) */
export type OrchestrationModeCode = 'SEQUENTIAL' | 'PARALLEL' | 'VOTING' | 'CASCADE'

/** 编排模式枚举（含 code 与中文描述，用于模式切换下拉） */
export interface OrchestrationMode {
  /** 模式编码 */
  code: OrchestrationModeCode
  /** 模式中文描述 */
  desc: string
}

/** Agent 类型元信息（用于编排页面下拉） */
export interface AgentTypeInfo {
  /** Agent 类型编码 */
  code: string
  /** Agent 类型中文描述 */
  desc: string
  /** 默认告警等级 */
  defaultAlertLevel?: string
}

/** 单个 Agent 的协调结果（trace 单条，用于决策追踪） */
export interface AgentTraceEntry {
  /** Agent 类型 */
  agentType: string
  /** 该步使用的编排模式 */
  mode?: OrchestrationModeCode | null
  /** 评分 */
  score?: number | null
  /** 置信度 */
  confidence?: number | null
  /** 备注 */
  note?: string | null
  /** 时间戳（ms） */
  ts: number
}

/** 单个 Agent 的输出 */
export interface AgentResultPayload {
  /** Agent 类型 */
  agentType?: string
  /** 告警等级 */
  alertLevel?: string
  /** 评分 */
  score?: number
  /** 置信度 */
  confidence?: number
  /** 处置建议 */
  suggestion?: string
  /** 命中的规则列表 */
  matchedRules?: string[]
  /** 原始附加数据 */
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
