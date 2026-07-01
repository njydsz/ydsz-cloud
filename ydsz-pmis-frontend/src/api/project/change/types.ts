/**
 * 项目变更管理 API 类型定义
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (批次 19 补全)
 */

/** 项目变更主表 VO */
export interface ProjectChangeVO {
  id: number
  /** 变更编号，唯一索引 */
  changeCode: string
  /** 关联立项 ID */
  initiationId: number
  initiationName?: string
  /** SCOPE/COST/CONTRACT/STAFF/SCHEDULE */
  changeType: string
  /** 变更标题 */
  changeTitle: string
  /** 变更原因 */
  changeReason?: string
  /** 详细描述 */
  changeDesc?: string

  // 影响评估字段（ChangeImpactEvaluator 计算）
  /** 预算影响（正=增加，负=减少） */
  budgetImpact?: number
  /** 合同金额影响 */
  contractImpact?: number
  /** 进度影响天数（正=延期，负=提前） */
  scheduleImpactDays?: number
  /** 利润影响（元） */
  profitImpact?: number
  /** 利润影响百分比 (-1~1) */
  profitImpactPct?: number
  /** 变更后风险等级 LOW/MEDIUM/HIGH */
  riskLevelAfter?: string
  /** 影响的 WBS 任务数 */
  affectedWbsCount?: number
  /** 影响的人员数 */
  affectedStaffCount?: number

  /** 重大变更标识 0/1 */
  majorFlag?: number
  /** 审批角色 JSON 数组字符串 */
  approverRoles?: string

  applicantId?: number
  applicantName?: string
  /** 关联合同（可选） */
  contractId?: number
  contractName?: string
  /** 关联流程实例 ID */
  workflowId?: string
  /**
   * 状态机：
   * DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED
   * APPROVED → EXECUTING → EXECUTED
   * DRAFT/SUBMITTED/UNDER_REVIEW/APPROVED/EXECUTING → CANCELLED
   * 终态：EXECUTED/REJECTED/CANCELLED
   */
  status: string
  submittedAt?: string
  approvedAt?: string
  executedAt?: string
  remark?: string
  tenantId?: number
  /** 链路追踪 ID */
  providerTraceId?: string
  createdBy?: number
  createdAt?: string
  updatedBy?: number
  updatedAt?: string
}

/** 创建项目变更 DTO */
export interface ProjectChangeCreateDTO {
  changeCode: string
  initiationId: number
  /** SCOPE/COST/CONTRACT/STAFF/SCHEDULE */
  changeType: string
  changeTitle: string
  changeReason?: string
  changeDesc?: string
  budgetImpact?: number
  contractImpact?: number
  scheduleImpactDays?: number
  profitImpact?: number
  affectedWbsCount?: number
  affectedStaffCount?: number
  contractId?: number
  applicantId?: number
  applicantName?: string
  remark?: string
  tenantId?: number
}

/** 状态迁移 DTO */
export interface ProjectChangeStatusDTO {
  id: number
  targetStatus: string
}

/** 按变更类型聚合行 */
export interface ProjectChangeAggregateRow {
  changeType: string
  count: number
  majorCount: number
}

/** 按状态聚合行 */
export interface ProjectChangeStatusAggregateRow {
  status: string
  count: number
}
