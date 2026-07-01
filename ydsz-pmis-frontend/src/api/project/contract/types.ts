/**
 * @file 合同管理 API 类型定义
 * @description 定义合同（Contract）模块的主合同、合同模板、合同变更相关的 VO/DTO 类型；
 *              与后端 ContractController 的请求/响应结构保持一致。
 * @module api/project/contract
 */

/** 合同主表 VO（视图对象，用于详情与列表展示） */
export interface ContractVO {
  /** 合同 ID */
  id: number
  /** 合同编号（唯一） */
  contractCode: string
  /** 合同名称 */
  contractName: string
  /** 关联立项 ID */
  initiationId?: number
  /** 关联立项名称 */
  initiationName?: string
  /** 客户 ID */
  customerId: number
  /** 客户名称 */
  customerName?: string
  /** 合同类型 */
  contractType?: string
  /** 合同金额（元） */
  amount?: number
  /** 币种 */
  currency?: string
  /** 签订日期 */
  signDate?: string
  /** 生效日期 */
  effectiveDate?: string
  /** 到期日期 */
  expireDate?: string
  /** 付款条款 */
  paymentTerms?: string
  /** DRAFT/UNDER_REVIEW/APPROVED/SIGNED/EXECUTING/CLOSED/REJECTED */
  status?: string
  /** 责任人 ID */
  ownerId?: number
  /** 责任人名称 */
  ownerName?: string
  /** 合同模板 ID */
  templateId?: number
  /** 风险等级 */
  riskLevel?: string
  /** 风险评分 */
  riskScore?: number
  /** 合同描述 */
  description?: string
  /** 租户 ID */
  tenantId?: number
  /** 创建时间 */
  createdAt?: string
  /** 更新时间 */
  updatedAt?: string
}

/** 合同创建 DTO */
export interface ContractCreateDTO {
  /** 合同编号（唯一） */
  contractCode: string
  /** 合同名称 */
  contractName: string
  /** 关联立项 ID */
  initiationId?: number
  /** 客户 ID */
  customerId: number
  /** 客户名称 */
  customerName?: string
  /** 合同类型 */
  contractType: string
  /** 合同金额（元） */
  amount: number
  /** 币种 */
  currency?: string
  /** 签订日期 */
  signDate?: string
  /** 生效日期 */
  effectiveDate?: string
  /** 到期日期 */
  expireDate?: string
  /** 付款条款 */
  paymentTerms?: string
  /** 责任人 ID */
  ownerId?: number
  /** 合同模板 ID */
  templateId?: number
  /** 合同描述 */
  description?: string
}

/** 合同状态变更 DTO */
export interface ContractStatusDTO {
  /** 合同 ID */
  id: number
  /** 目标状态 */
  targetStatus: string
  /** 变更原因（如驳回原因等） */
  reason?: string
}

/** 合同模板 VO */
export interface ContractTemplateVO {
  /** 模板 ID */
  id: number
  /** 模板编号 */
  code: string
  /** 模板名称 */
  name: string
  /** FIXED_PRICE/T&M/MILESTONE/RETAINER/LICENSE/SaaS/MAINTENANCE/OTHER */
  type?: string
  /** 模板版本 */
  version?: string
  /** 模板内容 */
  content?: string
  /** DRAFT/PUBLISHED/DEPRECATED */
  status?: string
  /** 模板描述 */
  description?: string
  /** 创建人 ID */
  createdBy?: number
  /** 创建时间 */
  createdAt?: string
}

/** 合同模板创建 DTO */
export interface ContractTemplateCreateDTO {
  /** 模板编号 */
  code: string
  /** 模板名称 */
  name: string
  /** 模板类型：FIXED_PRICE/T&M/MILESTONE/RETAINER/LICENSE/SaaS/MAINTENANCE/OTHER */
  type: string
  /** 模板版本 */
  version?: string
  /** 模板内容 */
  content: string
  /** 模板描述 */
  description?: string
}

/** 合同模板状态变更 DTO */
export interface ContractTemplateStatusDTO {
  /** 模板 ID */
  id: number
  /** 目标状态：DRAFT/PUBLISHED/DEPRECATED */
  targetStatus: string
}

/** 合同变更 VO */
export interface ContractChangeVO {
  /** 变更记录 ID */
  id: number
  /** 关联合同 ID */
  contractId: number
  /** 关联合同编号 */
  contractCode?: string
  /** SCOPE/COST/TERM/STAFF/SCHEDULE/OTHER */
  changeType?: string
  /** LOW/MEDIUM/HIGH */
  impactLevel?: string
  /** 影响评分 */
  impactScore?: number
  /** 变更原因 */
  reason?: string
  /** 变更前取值 */
  beforeValue?: string
  /** 变更后取值 */
  afterValue?: string
  /** DRAFT/SUBMITTED/UNDER_REVIEW/APPROVED/REJECTED/CLOSED/CANCELLED */
  status?: string
  /** 申请人 ID */
  applicantId?: number
  /** 申请人名称 */
  applicantName?: string
  /** 审批人 ID */
  approverId?: number
  /** 审批人名称 */
  approverName?: string
  /** 创建时间 */
  createdAt?: string
}

/** 合同变更创建 DTO */
export interface ContractChangeCreateDTO {
  /** 关联合同 ID */
  contractId: number
  /** 变更类型：SCOPE/COST/TERM/STAFF/SCHEDULE/OTHER */
  changeType: string
  /** 变更原因 */
  reason: string
  /** 变更前取值 */
  beforeValue?: string
  /** 变更后取值 */
  afterValue?: string
  /** 变更描述 */
  description?: string
}

/** 合同变更状态变更 DTO */
export interface ContractChangeStatusDTO {
  /** 变更记录 ID */
  id: number
  /** 目标状态：DRAFT/SUBMITTED/UNDER_REVIEW/APPROVED/REJECTED/CLOSED/CANCELLED */
  targetStatus: string
  /** 变更原因（如驳回原因等） */
  reason?: string
}
