/**
 * @file 项目变更管理 API 类型定义
 * @description 定义项目变更（ProjectChange）模块的 VO/DTO 及聚合行类型；
 *              与后端 ProjectChangeController 的请求/响应结构保持一致。批次 19 补全。
 * @module api/initiation/change
 * @author ydsz-pmis-team
 * @since 1.0.0
 */

/** 项目变更主表 VO */
export interface ProjectChangeVO {
  /** 变更记录 ID */
  id: number
  /** 变更编号，唯一索引 */
  changeCode: string
  /** 关联立项 ID */
  initiationId: number
  /** 关联立项名称 */
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

  /** 申请人 ID */
  applicantId?: number
  /** 申请人名称 */
  applicantName?: string
  /** 关联合同（可选） */
  contractId?: number
  /** 关联合同名称 */
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
  /** 提交时间 */
  submittedAt?: string
  /** 审批时间 */
  approvedAt?: string
  /** 执行完成时间 */
  executedAt?: string
  /** 备注 */
  remark?: string
  /** 租户 ID */
  tenantId?: number
  /** 链路追踪 ID */
  providerTraceId?: string
  /** 创建人 ID */
  createdBy?: number
  /** 创建时间 */
  createdAt?: string
  /** 更新人 ID */
  updatedBy?: number
  /** 更新时间 */
  updatedAt?: string
}

/** 创建项目变更 DTO */
export interface ProjectChangeCreateDTO {
  /** 变更编号（唯一） */
  changeCode: string
  /** 关联立项 ID */
  initiationId: number
  /** SCOPE/COST/CONTRACT/STAFF/SCHEDULE */
  changeType: string
  /** 变更标题 */
  changeTitle: string
  /** 变更原因 */
  changeReason?: string
  /** 详细描述 */
  changeDesc?: string
  /** 预算影响（正=增加，负=减少） */
  budgetImpact?: number
  /** 合同金额影响 */
  contractImpact?: number
  /** 进度影响天数（正=延期，负=提前） */
  scheduleImpactDays?: number
  /** 利润影响（元） */
  profitImpact?: number
  /** 影响的 WBS 任务数 */
  affectedWbsCount?: number
  /** 影响的人员数 */
  affectedStaffCount?: number
  /** 关联合同 ID（可选） */
  contractId?: number
  /** 申请人 ID */
  applicantId?: number
  /** 申请人名称 */
  applicantName?: string
  /** 备注 */
  remark?: string
  /** 租户 ID */
  tenantId?: number
}

/** 状态迁移 DTO */
export interface ProjectChangeStatusDTO {
  /** 变更记录 ID */
  id: number
  /** 目标状态 */
  targetStatus: string
}

/** 按变更类型聚合行 */
export interface ProjectChangeAggregateRow {
  /** 变更类型：SCOPE/COST/CONTRACT/STAFF/SCHEDULE */
  changeType: string
  /** 变更总数 */
  count: number
  /** 重大变更数 */
  majorCount: number
}

/** 按状态聚合行 */
export interface ProjectChangeStatusAggregateRow {
  /** 状态代码 */
  status: string
  /** 该状态下的变更数 */
  count: number
}
