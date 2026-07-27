/**
 * @file 费用报销管理类型定义
 * @description 定义费用报销的视图对象、创建 DTO 及审批 DTO 等数据结构，
 *              供 api/finance/expense 模块及业务页面使用。
 * @module api/finance/expense/types
 */

export interface ExpenseVO {
  /** 费用报销 ID */
  id: string
  /** 费用报销编码 */
  expenseCode: string
  /** 所属立项 ID */
  initiationId?: number
  /** 所属立项名称 */
  initiationName?: string
  /** 报销员工 ID */
  employeeId: number
  /** 报销员工姓名 */
  employeeName?: string
  /** 费用类型（如差旅、办公、招待等） */
  expenseType?: string
  /** 报销金额 */
  amount: number
  /** 费用发生日期 */
  expenseDate?: string
  /** 备注说明 */
  description?: string
  /** 发票/附件 URL */
  receiptUrl?: string
  /** 状态：DRAFT/SUBMITTED/APPROVED/REJECTED/PAID/CANCELLED */
  status?: string
  /** 审批人 ID */
  approverId?: number
  /** 审批人姓名 */
  approverName?: string
  /** 审批时间 */
  approvedAt?: string
  /** 创建时间 */
  createdAt?: string
}

export interface ExpenseCreateDTO {
  /** 费用报销编码 */
  expenseCode: string
  /** 所属立项 ID */
  initiationId?: number
  /** 报销员工 ID */
  employeeId: number
  /** 费用类型 */
  expenseType?: string
  /** 报销金额 */
  amount: number
  /** 费用发生日期 */
  expenseDate?: string
  /** 备注说明 */
  description?: string
  /** 发票/附件 URL */
  receiptUrl?: string
}

export interface ApprovalDTO {
  /** 费用报销 ID */
  id: string
  /** 目标状态 */
  targetStatus: string
  /** 审批人 ID */
  approverId?: number
  /** 审批人姓名 */
  approverName?: string
  /** 审批/驳回原因 */
  reason?: string
}
