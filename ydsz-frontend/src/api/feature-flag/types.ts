/**
 * @file 特性开关类型定义 (批次 20 P2-3)
 * @description 定义特性开关分类、快照结构等类型，
 *              与后端 ydsz-system 服务返回结构对齐。
 * @module api/feature-flag/types
 */

/** flag 分类：基础设施 / 业务 / UI / 安全 */
export type FeatureFlagCategory = 'INFRASTRUCTURE' | 'BUSINESS' | 'UI' | 'SAFETY'

/** 单个 flag 的快照 */
export interface FeatureFlagSnapshot {
  /** 唯一键 (对应后端 FeatureFlag.name()) */
  key: string
  /** 分类 */
  category: FeatureFlagCategory
  /** 描述 */
  description: string
  /** config 显式值, null = 未配置 */
  configuredValue: boolean | null
  /** 实际生效值 */
  effectiveValue: boolean
  /** 是否强制开启 (SAFETY 永远 true) */
  mandatory: boolean
  /** 灰度发布比例 0-100, null = 未设置 */
  rolloutPercentage: number | null
  /** 更新时间 (ISO 8601) */
  updatedAt: string
}
