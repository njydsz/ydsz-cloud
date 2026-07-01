/**
 * @file 采购申请管理类型定义
 * @description 定义采购申请的视图对象、创建 DTO 及审批 DTO 等数据结构，
 *              供 api/execution/purchase 模块及业务页面使用。
 * @module api/execution/purchase/types
 */

export interface PurchaseVO {
  /** 采购申请 ID */
  id: number
  /** 采购申请编码 */
  purchaseCode: string
  /** 所属立项 ID */
  initiationId: number
  /** 所属立项名称 */
  initiationName?: string
  /** 供应商名称 */
  vendor?: string
  /** 采购物品名称 */
  itemName: string
  /** 采购数量 */
  quantity?: number
  /** 单价 */
  unitPrice?: number
  /** 采购总金额 */
  amount?: number
  /** 采购日期 */
  purchaseDate?: string
  /** 状态：DRAFT/SUBMITTED/APPROVED/REJECTED/RECEIVED/PAID/CANCELLED */
  status?: string
  /** 申请人 ID */
  applicantId?: number
  /** 申请人姓名 */
  applicantName?: string
  /** 审批人 ID */
  approverId?: number
  /** 审批人姓名 */
  approverName?: string
  /** 审批时间 */
  approvedAt?: string
  /** 备注说明 */
  description?: string
  /** 创建时间 */
  createdAt?: string
}

export interface PurchaseCreateDTO {
  /** 采购申请编码 */
  purchaseCode: string
  /** 所属立项 ID */
  initiationId: number
  /** 供应商名称 */
  vendor?: string
  /** 采购物品名称 */
  itemName: string
  /** 采购数量 */
  quantity?: number
  /** 单价 */
  unitPrice?: number
  /** 采购总金额 */
  amount?: number
  /** 采购日期 */
  purchaseDate?: string
  /** 申请人 ID */
  applicantId: number
  /** 备注说明 */
  description?: string
}

export interface ApprovalDTO {
  /** 采购申请 ID */
  id: number
  /** 目标状态 */
  targetStatus: string
  /** 审批人 ID */
  approverId?: number
  /** 审批人姓名 */
  approverName?: string
  /** 审批/驳回原因 */
  reason?: string
}
