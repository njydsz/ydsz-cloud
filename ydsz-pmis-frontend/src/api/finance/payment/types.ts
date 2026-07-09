/**
 * @file 回款类型定义
 * @description 包含回款 VO/DTO、回款核销明细 VO/DTO 类型定义，
 *              供 payment 子模块下的 API 与页面共用。
 * @module api/finance/payment/types
 */

/** 回款 VO */
export interface PaymentVO {
  /** 主键 ID */
  id: number
  /** 回款编码 */
  paymentCode: string
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
  /** 回款金额 */
  amount: number
  /** 付款方式 */
  paymentMethod?: string
  /** 付款日期 */
  paymentDate?: string
  /** 状态：PENDING/CONFIRMED/ALLOCATED/CANCELLED */
  status?: string
  /** 未核销金额 */
  unallocatedAmount?: number
  /** 收款银行账号 */
  bankAccount?: string
  /** 银行流水号 */
  bankRef?: string
  /** 备注 */
  remark?: string
  /** 创建时间 */
  createdAt?: string
}

/** 回款创建 DTO */
export interface PaymentCreateDTO {
  /** 回款编码 */
  paymentCode: string
  /** 客户 ID */
  customerId: number
  /** 客户名称 */
  customerName?: string
  /** 立项 ID */
  initiationId: number
  /** 合同 ID */
  contractId?: number
  /** 回款金额 */
  amount: number
  /** 付款方式 */
  paymentMethod?: string
  /** 付款日期 */
  paymentDate?: string
  /** 收款银行账号 */
  bankAccount?: string
  /** 银行流水号 */
  bankRef?: string
  /** 备注 */
  remark?: string
}

/** 回款核销分配 DTO */
export interface PaymentAllocationDTO {
  /** 回款 ID */
  paymentId: number
  /** 发票 ID */
  invoiceId: number
  /** 核销金额 */
  amount: number
}

/** 回款核销明细 VO */
export interface PaymentAllocationVO {
  /** 主键 ID */
  id: number
  /** 回款 ID */
  paymentId: number
  /** 发票 ID */
  invoiceId: number
  /** 发票编码 */
  invoiceCode?: string
  /** 核销金额 */
  amount: number
  /** 核销时间 */
  allocatedAt?: string
}
