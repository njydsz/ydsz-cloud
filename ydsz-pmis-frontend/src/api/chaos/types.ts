/**
 * 混沌工程 API 类型 (批次 24 P2-2)
 */
export type ChaosExperimentType =
  | 'LATENCY'
  | 'EXCEPTION'
  | 'ERROR_RATE'
  | 'RESOURCE_EXHAUSTION'
  | 'NETWORK_PARTITION'

export type ChaosOutcome =
  | 'INJECTED'
  | 'NOT_TRIGGERED'
  | 'BLOCKED_BY_FLAG'
  | 'SKIPPED_PROBABILITY'

export interface ChaosExperiment {
  type: ChaosExperimentType
  /** 目标方法/类名 (匹配前缀) */
  target: string
  /** LATENCY: 延迟毫秒数 */
  latencyMs?: number
  /** EXCEPTION: 异常类全限定名 */
  exceptionClass?: string
  /** ERROR_RATE: 错误率 0.0-1.0 */
  errorRate?: number
  /** 实验描述 */
  description?: string
  /** 是否启用 */
  enabled: boolean
  /** 创建者 */
  createdBy?: string
}

export interface ChaosEvent {
  timestamp: number
  target: string
  outcome: ChaosOutcome
  detail: string
}

export interface ChaosDryRunResult {
  target: string
  outcome: ChaosOutcome
  error: string
}
