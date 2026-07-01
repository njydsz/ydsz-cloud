/**
 * @file 经营驾驶舱类型定义（批次18 增强）
 * @description 包含经营驾驶舱、高管看板、预警事件、KPI 趋势等相关的 VO/DTO 类型定义，
 *              供 cockpit 子模块下的 API 与页面共用。
 * @module api/execution/cockpit/types
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
  /** EVM 健康度 - 红色项目数 */
  evmRedCount?: number
  /** EVM 健康度 - 黄色项目数 */
  evmYellowCount?: number
  /** EVM 健康度 - 绿色项目数 */
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
  /** 事件 ID */
  eventId?: string
  /** 规则编码 */
  ruleCode?: string
  /** 规则名称 */
  ruleName?: string
  /** 预警分类：EVM/COST/BENCH/CREDIT/RISK/UTILIZATION 等 */
  category?: 'EVM' | 'COST' | 'BENCH' | 'CREDIT' | 'RISK' | 'UTILIZATION' | string
  /** 严重程度：INFO/YELLOW/RED */
  severity?: 'INFO' | 'YELLOW' | 'RED' | string
  /** 预警标题 */
  title?: string
  /** 预警描述 */
  description?: string
  /** 当前实际值 */
  currentValue?: string
  /** 阈值 */
  threshold?: string
  /** 影响范围 */
  scope?: string
  /** 触发时间 */
  triggeredAt?: string
  /** 是否支持下钻 */
  drilldownAvailable?: boolean
}

/** 驾驶舱预警摘要 */
export interface CockpitAlertSummaryVO {
  /** 红色预警数量 */
  redCount?: number
  /** 黄色预警数量 */
  yellowCount?: number
  /** 信息级预警数量 */
  infoCount?: number
  /** 预警总数 */
  totalCount?: number
  /** 预警事件列表 */
  events?: AlertEventDTO[]
  /** 最重要的预警事件 */
  topEvent?: AlertEventDTO | null
}

/** 项目群 KPI */
export interface ProjectGroupKpiDTO {
  /** 项目群编码 */
  groupCode?: string
  /** 项目群名称 */
  groupName?: string
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
  /** 毛利率 */
  grossMargin?: number
  /** EVM 红色项目数 */
  evmRedCount?: number
}

/** 高管看板 */
export interface ExecutiveOverviewVO {
  // 顶部 KPI
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
  /** 毛利率 */
  grossMargin?: number
  /** 平均可计费利用率 */
  avgBillableUtilization?: number
  /** Bench 闲置成本 */
  benchIdleCost?: number
  // 健康度
  /** EVM 红色项目数 */
  evmRedCount?: number
  /** EVM 黄色项目数 */
  evmYellowCount?: number
  /** EVM 绿色项目数 */
  evmGreenCount?: number
  /** 健康项目占比 */
  healthRatio?: number
  /** 风险项目数 */
  riskProjectCount?: number
  /** 风险项目占比 */
  riskProjectRatio?: number
  // 项目群对比
  /** 各项目群 KPI 对比 */
  projectGroups?: ProjectGroupKpiDTO[]
  // 综合评分
  /** 健康度评分 */
  healthScore?: number
  /** 健康度等级：A/B/C/D */
  healthGrade?: 'A' | 'B' | 'C' | 'D' | string
}

/** KPI 趋势 */
export interface KpiTrendVO {
  /** 周期标签列表 */
  periods?: string[]
  /** 合同金额序列 */
  contractAmountSeries?: number[]
  /** 已确认收入序列 */
  confirmedRevenueSeries?: number[]
  /** 累计成本序列 */
  totalCostSeries?: number[]
  /** 毛利序列 */
  grossProfitSeries?: number[]
  /** 毛利率序列（百分比） */
  grossMarginPctSeries?: number[]
  /** 在执行项目数序列 */
  activeProjectsSeries?: number[]
  /** 合同金额环比增长率 */
  contractMtdGrowth?: number
  /** 收入环比增长率 */
  revenueMtdGrowth?: number
  /** 利润环比增长率 */
  profitMtdGrowth?: number
}
