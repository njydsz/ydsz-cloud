/**
 * @file AI 智能体预测结果相关类型定义
 * @description 定义 Agent 类型、告警等级、执行状态、执行请求与预测结果等类型，
 *              供 prediction/index.ts 及业务侧使用。
 * @module api/agent/prediction
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */

/** Agent 类型枚举：风险预警 / 资源推荐 / 利润预测 / 中标率预测 / 工时异常 */
export type AgentTypeCode =
  | 'RISK_WARNING'
  | 'RESOURCE_RECOMMEND'
  | 'PROFIT_FORECAST'
  | 'WIN_RATE_PREDICT'
  | 'TIMESHEET_ANOMALY'

/** 告警等级（严重性顺序：RED > YELLOW > INFO=NORMAL=RECOMMEND） */
export type AlertLevel = 'NORMAL' | 'INFO' | 'YELLOW' | 'RED' | 'RECOMMEND'

/** 执行状态：运行中 / 成功 / 失败 */
export type RunStatus = 'RUNNING' | 'SUCCESS' | 'FAILED'

/** Agent 执行请求 */
export interface AgentRunRequest {
  /** Agent 类型编码 */
  agentType: AgentTypeCode
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
  /** 输入参数（由具体 Agent 解释） */
  params?: Record<string, unknown>
}

/** Agent 预测结果（对应 AgentPredictionDO，含 provider_trace_id 追踪链路） */
export interface AgentPrediction {
  /** 主键 ID */
  id: number
  /** 任务编码（用于追踪 provider_trace_id） */
  taskCode?: string
  /** Agent 类型编码 */
  agentType: AgentTypeCode
  /** 关联业务类型：PROJECT / OPPORTUNITY / TIMESHEET / STAFF */
  bizType?: string
  /** 关联业务 ID */
  bizId?: number
  /** 关联业务编号/名称（冗余） */
  bizRef?: string
  /** 输入快照（JSON 字符串） */
  inputSnapshot?: string
  /** 输出结果（JSON 字符串） */
  outputResult?: string
  /** 告警等级（严重性顺序：RED > YELLOW > INFO=NORMAL=RECOMMEND） */
  alertLevel?: AlertLevel
  /** 评分 */
  score?: number
  /** 置信度 */
  confidence?: number
  /** 处置建议 */
  suggestion?: string
  /** 命中的规则列表 */
  matchedRules?: string[]
  /** 执行耗时（ms） */
  costMs?: number
  /** 模型版本 */
  modelVersion?: string
  /** 执行状态 */
  status?: RunStatus
  /** 错误信息（status=FAILED 时填充） */
  errorMsg?: string
  /** 调用人 ID */
  callerId?: number
  /** 调用人姓名 */
  callerName?: string
  /** 来源：MANUAL / SCHEDULED / EVENT */
  source?: string
  /** 创建时间 */
  createdAt?: string
  /** 更新时间 */
  updatedAt?: string
}
