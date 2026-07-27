/**
 * @file 每日对账类型定义
 * @description 包含每日对账 VO、按状态聚合统计 VO 类型定义，
 *              供 reconcile 子模块下的 API 与页面共用。
 * @module api/finance/reconcile/types
 */

/** 每日对账 VO */
export interface DailyReconcileVO {
  /** 主键 ID */
  id: string
  /** 对账日期 YYYY-MM-DD */
  reconcileDate: string
  /** 对账类型：COST/REVENUE/PAYMENT/INVOICE/PROFIT/LABOR */
  reconcileType: string
  /** 立项 ID */
  initiationId?: number
  /** 期望金额 */
  expectedAmount: number
  /** 实际金额 */
  actualAmount: number
  /** 差异金额 */
  diffAmount: number
  /** 差异百分比 0-1 */
  diffPct: number
  /** 状态：OK/WARN/ERROR */
  status: string
  /** 明细说明 */
  detail?: string
  /** 创建时间 */
  createdAt?: string
}

/** 按状态聚合统计 VO */
export interface DailyReconcileAggregateVO {
  /** 状态：OK/WARN/ERROR */
  status: string
  /** 记录数 */
  count: number
  /** 累计差异金额 */
  totalDiff: number
}
