/**
 * 经营驾驶舱 / 高管看板 / 预警事件 / KPI 趋势 类型定义（批次18 增强）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */

/** 驾驶舱总览 KPI */
export interface CockpitKpiVO {
  /** 在执行项目数 */
  activeProjects?: number
  /** 合同总额 */
  totalContractAmount?: number
  /** 已确认收入 */
  confirmedRevenue?: number
  /** 累计成本 */
  totalCost?: number
  /** 累计毛利 */
  grossProfit?: number
  /** 平均毛利率（0-1） */
  grossMargin?: number
  /** EVM 健康项目数 */
  evmRedCount?: number
  evmYellowCount?: number
  evmGreenCount?: number
  /** Bench 累计闲置成本 */
  benchIdleCost?: number
  /** 可计费利用率均值（0-1） */
  avgBillableUtilization?: number
  /** 维度下钻项 */
  dimensionBreakdown?: Array<Record<string, unknown>>
}

/** 预警事件 */
export interface AlertEventDTO {
  eventId?: string
  ruleCode?: string
  ruleName?: string
  category?: 'EVM' | 'COST' | 'BENCH' | 'CREDIT' | 'RISK' | 'UTILIZATION' | string
  severity?: 'INFO' | 'YELLOW' | 'RED' | string
  title?: string
  description?: string
  currentValue?: string
  threshold?: string
  scope?: string
  triggeredAt?: string
  drilldownAvailable?: boolean
}

/** 驾驶舱预警摘要 */
export interface CockpitAlertSummaryVO {
  redCount?: number
  yellowCount?: number
  infoCount?: number
  totalCount?: number
  events?: AlertEventDTO[]
  topEvent?: AlertEventDTO | null
}

/** 项目群 KPI */
export interface ProjectGroupKpiDTO {
  groupCode?: string
  groupName?: string
  activeProjects?: number
  totalContractAmount?: number
  confirmedRevenue?: number
  totalCost?: number
  grossProfit?: number
  grossMargin?: number
  evmRedCount?: number
}

/** 高管看板 */
export interface ExecutiveOverviewVO {
  // 顶部 KPI
  activeProjects?: number
  totalContractAmount?: number
  confirmedRevenue?: number
  totalCost?: number
  grossProfit?: number
  grossMargin?: number
  avgBillableUtilization?: number
  benchIdleCost?: number
  // 健康度
  evmRedCount?: number
  evmYellowCount?: number
  evmGreenCount?: number
  healthRatio?: number
  riskProjectCount?: number
  riskProjectRatio?: number
  // 项目群对比
  projectGroups?: ProjectGroupKpiDTO[]
  // 综合评分
  healthScore?: number
  healthGrade?: 'A' | 'B' | 'C' | 'D' | string
}

/** KPI 趋势 */
export interface KpiTrendVO {
  periods?: string[]
  contractAmountSeries?: number[]
  confirmedRevenueSeries?: number[]
  totalCostSeries?: number[]
  grossProfitSeries?: number[]
  grossMarginPctSeries?: number[]
  activeProjectsSeries?: number[]
  contractMtdGrowth?: number
  revenueMtdGrowth?: number
  profitMtdGrowth?: number
}
