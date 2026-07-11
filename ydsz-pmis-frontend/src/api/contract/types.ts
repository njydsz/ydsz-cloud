/**
 * @file 合同类型定义
 * @description 包含合同 VO/DTO 类型定义，供 contract 子模块下的 API 与页面共用。
 * @module api/contract/types
 */

/** 合同 VO */
export interface ContractVO {
  /** 主键 ID */
  id: number
  /** 合同编码 */
  contractCode: string
  /** 合同名称 */
  contractName: string
  /** 客户 ID */
  customerId: number
  /** 客户名称 */
  customerName?: string
  /** 关联立项 ID */
  initiationId?: number
  /** 关联立项名称 */
  initiationName?: string
  /** 合同类型：FIXED_PRICE/T_M/MILESTONE/RETAINER/LICENSE/SAAS/MAINTENANCE/OTHER */
  contractType?: string
  /** 合同金额 */
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
  /** 风险等级：LOW/MEDIUM/HIGH */
  riskLevel?: string
  /** 状态：DRAFT/UNDER_REVIEW/APPROVED/SIGNED/EXECUTING/CLOSED/REJECTED */
  status?: string
  /** 描述 */
  description?: string
  /** 创建时间 */
  createdAt?: string
}

/** 合同创建 DTO */
export interface ContractCreateDTO {
  /** 合同编码 */
  contractCode: string
  /** 合同名称 */
  contractName: string
  /** 客户 ID */
  customerId: number
  /** 客户名称 */
  customerName?: string
  /** 合同类型 */
  contractType: string
  /** 合同金额 */
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
  /** 描述 */
  description?: string
}

/** 合同状态变更 DTO */
export interface ContractStatusDTO {
  /** 合同 ID */
  id: number
  /** 目标状态 */
  targetStatus: string
  /** 变更原因 */
  reason?: string
}
