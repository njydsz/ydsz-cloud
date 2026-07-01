/** 每日对账 VO */
export interface DailyReconcileVO {
  id: number
  /** 对账日期 YYYY-MM-DD */
  reconcileDate: string
  /** COST/REVENUE/PAYMENT/INVOICE/PROFIT/LABOR */
  reconcileType: string
  initiationId?: number
  expectedAmount: number
  actualAmount: number
  diffAmount: number
  /** 差异百分比 0-1 */
  diffPct: number
  /** OK/WARN/ERROR */
  status: string
  detail?: string
  createdAt?: string
}

/** 按状态聚合统计 */
export interface DailyReconcileAggregateVO {
  status: string
  count: number
  totalDiff: number
}
