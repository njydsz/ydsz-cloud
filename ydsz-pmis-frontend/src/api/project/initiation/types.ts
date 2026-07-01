/**
 * @file 立项管理 API 类型定义
 * @description 定义立项（Initiation）模块及其预算明细、门径评审相关的 VO/DTO 类型；
 *              与后端 InitiationController 的请求/响应结构保持一致。
 * @module api/project/initiation
 */

/** 立项 VO（视图对象，用于详情与列表展示） */
export interface InitiationVO {
  /** 立项 ID */
  id: number
  /** 项目编号（唯一） */
  projectCode: string
  /** 项目名称 */
  projectName: string
  /** 关联商机 ID（可空，非商机转立项场景为空） */
  opportunityId?: number
  /** 客户 ID */
  customerId: number
  /** 客户名称 */
  customerName?: string
  /** 商务部门 ID */
  businessDeptId?: number
  /** 项目类型 */
  projectType?: string
  /** 项目分级 */
  projectLevel?: string
  /** 项目经理 ID */
  pmId?: number
  /** 项目经理名称 */
  pmName?: string
  /** 发起人 ID */
  sponsorId?: number
  /** 发起人名称 */
  sponsorName?: string
  /** 预估金额（元） */
  estimatedAmount?: number
  /** 预算金额（元） */
  budgetAmount?: number
  /** 计划开始日期 */
  plannedStartDate?: string
  /** 计划结束日期 */
  plannedEndDate?: string
  /** 工期天数 */
  durationDays?: number
  /** 当前阶段 */
  stage?: string
  /** 当前门径代码 */
  currentGate?: string
  /** 项目描述 */
  description?: string
  /** 商业案例 */
  businessCase?: string
  /** 风险评估 */
  riskAssessment?: string
  /** 审批流程实例 ID */
  workflowId?: string
  /** 租户 ID */
  tenantId?: number
  /** 创建时间 */
  createdAt?: string
  /** 更新时间 */
  updatedAt?: string
}

/** 立项创建 DTO */
export interface InitiationCreateDTO {
  /** 项目编号（唯一） */
  projectCode: string
  /** 项目名称 */
  projectName: string
  /** 关联商机 ID（可空） */
  opportunityId?: number
  /** 客户 ID */
  customerId: number
  /** 客户名称 */
  customerName?: string
  /** 商务部门 ID */
  businessDeptId?: number
  /** 项目类型 */
  projectType: string
  /** 项目分级 */
  projectLevel?: string
  /** 项目经理 ID */
  pmId?: number
  /** 项目经理名称 */
  pmName?: string
  /** 发起人 ID */
  sponsorId?: number
  /** 发起人名称 */
  sponsorName?: string
  /** 预估金额（元） */
  estimatedAmount?: number
  /** 预算金额（元） */
  budgetAmount?: number
  /** 计划开始日期 */
  plannedStartDate?: string
  /** 计划结束日期 */
  plannedEndDate?: string
  /** 项目描述 */
  description?: string
  /** 商业案例 */
  businessCase?: string
  /** 风险评估 */
  riskAssessment?: string
}

/** 立项阶段迁移 DTO */
export interface InitiationStageDTO {
  /** 立项 ID */
  id: number
  /** 目标阶段 */
  targetStage: string
  /** 门径代码（可选） */
  gate?: string
}

/** 预算明细创建 DTO */
export interface BudgetItemDTO {
  /** 立项 ID */
  initiationId: number
  /** 预算分类 */
  category: string
  /** 预算项名称 */
  itemName: string
  /** 金额（元） */
  amount: number
  /** 备注 */
  remark?: string
}

/** 预算明细 VO */
export interface BudgetItemVO {
  /** 预算明细 ID */
  id: number
  /** 立项 ID */
  initiationId: number
  /** 预算分类 */
  category: string
  /** 预算项名称 */
  itemName: string
  /** 金额（元） */
  amount: number
  /** 备注 */
  remark?: string
  /** 创建时间 */
  createdAt?: string
}

/** 门径评审 DTO */
export interface GateReviewDTO {
  /** 立项 ID */
  initiationId: number
  /** CD1_KICKOFF/CD2_DESIGN/CD3_BUILD/CD4_UAT/CD5_GO_LIVE */
  gateCode: string
  /** PASS/FAIL/CONDITIONAL */
  reviewResult: string
  /** 评审人 ID */
  reviewerId?: number
  /** 评审人名称 */
  reviewerName?: string
  /** 评审意见 */
  comment?: string
}

/** 门径评审记录 VO */
export interface GateReviewVO {
  /** 评审记录 ID */
  id: number
  /** 立项 ID */
  initiationId: number
  /** 门径代码：CD1_KICKOFF/CD2_DESIGN/CD3_BUILD/CD4_UAT/CD5_GO_LIVE */
  gateCode: string
  /** 评审结果：PASS/FAIL/CONDITIONAL */
  reviewResult: string
  /** 评审人 ID */
  reviewerId?: number
  /** 评审人名称 */
  reviewerName?: string
  /** 评审意见 */
  comment?: string
  /** 评审时间 */
  reviewedAt?: string
}
