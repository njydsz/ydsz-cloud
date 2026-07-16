/**
 * @file 混沌工程 API 类型 (批次 24 P2-2)
 * @description 定义混沌实验类型、实验结果、注入事件等类型，
 *              与后端 com.njydsz.config.controller.ChaosController 返回结构对齐。
 * @module api/chaos/types
 */

/** 实验类型：延迟 / 抛异常 / 错误率 / 资源耗尽 / 网络分区 */
export type ChaosExperimentType =
  | 'LATENCY'
  | 'EXCEPTION'
  | 'ERROR_RATE'
  | 'RESOURCE_EXHAUSTION'
  | 'NETWORK_PARTITION'

/** 注入结果：已注入 / 未触发 / 被开关拦截 / 概率跳过 */
export type ChaosOutcome =
  | 'INJECTED'
  | 'NOT_TRIGGERED'
  | 'BLOCKED_BY_FLAG'
  | 'SKIPPED_PROBABILITY'

/**
 * 混沌实验配置
 */
export interface ChaosExperiment {
  /** 实验类型 */
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

/**
 * 混沌注入事件
 */
export interface ChaosEvent {
  /** 事件时间戳（毫秒） */
  timestamp: number
  /** 目标方法/类名 */
  target: string
  /** 注入结果 */
  outcome: ChaosOutcome
  /** 详情说明 */
  detail: string
}

/**
 * dry-run 注入结果
 */
export interface ChaosDryRunResult {
  /** 目标方法/类名 */
  target: string
  /** 注入结果 */
  outcome: ChaosOutcome
  /** 错误信息（注入失败时返回） */
  error: string
}
