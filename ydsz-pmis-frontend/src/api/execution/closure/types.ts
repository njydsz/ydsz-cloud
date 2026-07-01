/**
 * @file 项目结项类型定义
 * @description 包含项目结项 VO/DTO 类型定义，供 closure 子模块下的 API 与页面共用。
 * @module api/execution/closure/types
 */

/** 项目结项 VO */
export interface ProjectClosureVO {
  /** 主键 ID */
  id: number
  /** 结项编号 */
  closureCode: string
  /** 立项 ID */
  initiationId: number
  /** 立项名称 */
  initiationName?: string
  /** 结项类型：FORMAL/PRE_CLOSURE/FORCED */
  type?: string
  /** 状态：DRAFT/SUBMITTED/APPROVED/REJECTED/ARCHIVED */
  status?: string
  /** 结项原因 */
  reason?: string
  /** 项目总结 */
  summary?: string
  /** 经验教训 */
  lessonsLearned?: string
  /** 质保结束日期 */
  warrantyEndDate?: string
  /** 回款比例 */
  paymentRatio?: number
  /** 毛利率 */
  grossMargin?: number
  /** 申请人 ID */
  applicantId?: number
  /** 申请人姓名 */
  applicantName?: string
  /** 审批人 ID */
  approverId?: number
  /** 审批人姓名 */
  approverName?: string
  /** 创建时间 */
  createdAt?: string
}

/** 项目结项创建 DTO */
export interface ProjectClosureCreateDTO {
  /** 结项编号 */
  closureCode: string
  /** 立项 ID */
  initiationId: number
  /** 结项类型 */
  type: string
  /** 结项原因 */
  reason?: string
  /** 项目总结 */
  summary?: string
  /** 经验教训 */
  lessonsLearned?: string
  /** 质保结束日期 */
  warrantyEndDate?: string
}

/** 项目结项状态变更 DTO */
export interface ProjectClosureStatusDTO {
  /** 结项申请 ID */
  id: number
  /** 目标状态 */
  targetStatus: string
  /** 变更原因（驳回时填写） */
  reason?: string
}
