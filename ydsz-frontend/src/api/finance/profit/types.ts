/**
 * @file 收入与利润快照类型定义
 * @description 定义收入确认记录、利润快照的视图对象与创建 DTO 等数据结构，
 *              供 api/finance/profit 模块及业务页面使用。
 * @module api/finance/profit/types
 */

export interface RevenueVO {
  /** 收入确认记录 ID */
  id: number
  /** 所属立项 ID */
  initiationId: number
  /** 所属立项名称 */
  initiationName?: string
  /** 关联合同 ID */
  contractId?: number
  /** 关联合同编码 */
  contractCode?: string
  /** 收入确认方法：FINAL/MILESTONE/MONTHLY */
  recognitionMethod?: string
  /** 确认金额 */
  amount: number
  /** 所属周期（YYYY-MM） */
  period: string
  /** 确认日期 */
  recognitionDate?: string
  /** 备注说明 */
  description?: string
  /** 状态 */
  status?: string
  /** 创建时间 */
  createdAt?: string
}

export interface RevenueCreateDTO {
  /** 所属立项 ID */
  initiationId: number
  /** 关联合同 ID */
  contractId?: number
  /** 收入确认方法 */
  recognitionMethod: string
  /** 确认金额 */
  amount: number
  /** 所属周期（YYYY-MM） */
  period: string
  /** 确认日期 */
  recognitionDate?: string
  /** 备注说明 */
  description?: string
}

export interface ProfitSnapshotVO {
  /** 利润快照 ID */
  id: number
  /** 所属立项 ID */
  initiationId: number
  /** 所属立项名称 */
  initiationName?: string
  /** 所属周期（YYYY-MM） */
  period: string
  /** 收入金额 */
  revenue: number
  /** 人力成本 */
  laborCost: number
  /** 采购成本 */
  purchaseCost: number
  /** 费用成本 */
  expenseCost: number
  /** 分摊成本 */
  allocatedCost: number
  /** 总成本 */
  totalCost: number
  /** 毛利润 */
  grossProfit: number
  /** 毛利率（百分比） */
  grossMargin: number
  /** 完工估算（EAC, Estimate At Completion） */
  eac?: number
  /** 项目健康度评分 */
  healthScore?: number
  /** 创建时间 */
  createdAt?: string
}
