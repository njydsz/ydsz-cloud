/**
 * @file Token 配额类型定义
 * @module api/agent/token-quota
 * @author ydsz-pmis-team
 * @since 1.0.0
 */

/** 配额概览 */
export interface QuotaSummary {
  tenantId?: string
  monthlyQuota: number
  usedTokens: number
  remainingTokens: number
  usagePercentage: number
  period: string
  resetAt?: string
}

/** Token 用量日志 */
export interface TokenUsageLog {
  id: string
  tenantId?: string
  agentType: string
  provider: string
  model: string
  promptTokens: number
  completionTokens: number
  totalTokens: number
  costMs?: number
  bizType?: string
  bizRef?: string
  traceId?: string
  createdAt: string
}
