/**
 * @file 发票类型定义
 * @description 包含发票 VO/DTO 类型定义，供 invoice 子模块下的 API 与页面共用。
 * @module api/execution/invoice/types
 */

/** 发票 VO */
export interface InvoiceVO {
  /** 主键 ID */
  id: number
  /** 发票编码（系统生成） */
  invoiceCode: string
  /** 发票号码（税务编号） */
  invoiceNo?: string
  /** 发票类型 */
  invoiceType?: string
  /** 开票依据：MILESTONE/OUTSOURCING/MONTHLY/FINAL/OTHER */
  invoiceBasis?: string
  /** 客户 ID */
  customerId: number
  /** 客户名称 */
  customerName?: string
  /** 立项 ID */
  initiationId: number
  /** 立项名称 */
  initiationName?: string
  /** 合同 ID */
  contractId?: number
  /** 合同编号 */
  contractCode?: string
  /** 金额 */
  amount: number
  /** 税率 */
  taxRate?: number
  /** 税额 */
  taxAmount?: number
  /** 状态：DRAFT/SUBMITTED/APPROVED/REJECTED/ISSUED/RED_REVERSED/CANCELLED */
  status?: string
  /** 开票日期 */
  issueDate?: string
  /** 到期日期 */
  dueDate?: string
  /** 红冲关联发票 ID */
  reversedById?: number
  /** 备注 */
  remark?: string
  /** 创建时间 */
  createdAt?: string
}

/** 发票创建 DTO */
export interface InvoiceCreateDTO {
  /** 发票编码 */
  invoiceCode: string
  /** 发票类型 */
  invoiceType: string
  /** 开票依据：MILESTONE/OUTSOURCING/MONTHLY/FINAL/OTHER */
  invoiceBasis: string
  /** 客户 ID */
  customerId: number
  /** 客户名称 */
  customerName?: string
  /** 立项 ID */
  initiationId: number
  /** 合同 ID */
  contractId?: number
  /** 金额 */
  amount: number
  /** 税率 */
  taxRate?: number
  /** 税额 */
  taxAmount?: number
  /** 到期日期 */
  dueDate?: string
  /** 描述说明 */
  description?: string
  /** MILESTONE 必填：验收证明 URL */
  acceptanceProof?: string
  /** OUTSOURCING 必填：人天确认单 URL */
  personDaySheet?: string
}

/** 发票审批/开具/红冲 DTO */
export interface InvoiceApprovalDTO {
  /** 发票 ID */
  id: number
  /** 审批人 ID */
  approverId?: number
  /** 审批人姓名 */
  approverName?: string
  /** 审批/红冲原因 */
  reason?: string
  /** 红冲关联发票 ID */
  reversedById?: number
}
