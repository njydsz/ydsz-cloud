/**
 * @file HITL 人工审批类型定义
 * @module api/agent/hitl
 * @author ydsz-pmis-team
 * @since 1.0.0
 */

/** 审批状态 */
export type HitlApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'EXPIRED'

/** 审批请求 DO */
export interface HitlApprovalRequest {
  id: string
  agentType: string
  bizType: string
  bizId: string
  bizRef?: string
  status: HitlApprovalStatus
  snapshot?: string
  question?: string
  options?: string[]
  recommendation?: string
  approverId?: string
  approverName?: string
  comment?: string
  sessionId?: string
  traceId?: string
  createdAt: string
  updatedAt: string
  expiredAt?: string
}

/** 审批操作 DTO */
export interface HitlApprovalActionDTO {
  approverId: string
  approverName: string
  comment?: string
}

/** ReAct 执行结果 */
export interface ReActResult {
  answer?: string
  steps?: Array<{
    step: number
    thought?: string
    action?: string
    actionInput?: string
    observation?: string
  }>
  totalCostMs?: number
  totalTokens?: number
}
