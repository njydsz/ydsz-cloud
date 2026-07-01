/**
 * @file 客户信用类型定义
 * @description 包含客户信用 VO/DTO 类型定义，供 customer-credit 子模块下的 API 与页面共用。
 * @module api/execution/customer-credit/types
 */

/** 客户信用 VO */
export interface CustomerCreditVO {
  /** 主键 ID */
  id: number
  /** 客户 ID */
  customerId: number
  /** 客户名称 */
  customerName?: string
  /** 信用等级：A/B/C/D */
  level?: string
  /** 信用评分 */
  score: number
  /** 合同数量 */
  contractCount?: number
  /** 合同总额 */
  totalContractAmount?: number
  /** 逾期次数 */
  overdueCount?: number
  /** 逾期金额 */
  overdueAmount?: number
  /** 最近评估日期 */
  lastAssessDate?: string
  /** 备注 */
  remark?: string
}

/** 客户信用评估 DTO */
export interface CreditAssessmentDTO {
  /** 客户 ID */
  customerId: number
  /** 客户名称 */
  customerName?: string
  /** 合同数量 */
  contractCount?: number
  /** 合同总额 */
  totalContractAmount?: number
  /** 逾期次数 */
  overdueCount?: number
  /** 逾期金额 */
  overdueAmount?: number
  /** 合作年限（年） */
  cooperationYears?: number
  /** 付款习惯：ONTIME/OFTEN_LATE/SEVERE_LATE */
  paymentHabit?: string
}
