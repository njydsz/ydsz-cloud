/**
 * @file EVM 测量相关 VO / DTO 类型定义
 * @description 定义挣值管理模块的视图对象（VO）与数据传输对象（DTO），
 *              供 EvmController 相关接口出入参使用。
 * @module api/execution/evm/types
 */

/** EVM 测量记录视图对象 */
export interface EvmMeasureVO {
  /** 测量记录ID */
  id?: number
  /** 立项ID */
  initiationId: number
  /** WBS 任务ID */
  wbsTaskId?: number
  /** 测量周期（如 2024-06） */
  period: string
  /** 计划价值 PV（Planned Value） */
  pv: number
  /** 挣值 EV（Earned Value） */
  ev: number
  /** 实际成本 AC（Actual Cost） */
  ac: number
  /** 完工预算 BAC（Budget At Completion） */
  bac: number
  /** 成本绩效指数 CPI（Cost Performance Index） */
  cpi?: number
  /** 进度绩效指数 SPI（Schedule Performance Index） */
  spi?: number
  /** 成本偏差 CV（Cost Variance） */
  cv?: number
  /** 进度偏差 SV（Schedule Variance） */
  sv?: number
  /** 完工估算 EAC（Estimate At Completion） */
  eac?: number
  /** 完工偏差 VAC（Variance At Completion） */
  vac?: number
  /** 完工尚需估算 ETC（Estimate To Complete） */
  etc?: number
  /** 完工尚需绩效指数 TCPI（To-Complete Performance Index） */
  tcpi?: number
  /** 预警等级：GREEN 正常 / YELLOW 警告 / RED 危险 */
  alertLevel?: 'GREEN' | 'YELLOW' | 'RED'
  /** 预警原因说明 */
  alertReason?: string
  /** 测量日期 */
  measureDate?: string
  /** 备注 */
  remark?: string
}

/** EVM 测量记录创建 DTO（按 initiation+wbs+period 幂等） */
export interface EvmMeasureCreateDTO {
  /** 立项ID */
  initiationId: number
  /** WBS 任务ID */
  wbsTaskId?: number
  /** 测量周期（如 2024-06） */
  period: string
  /** 计划价值 PV */
  pv: number
  /** 挣值 EV */
  ev: number
  /** 实际成本 AC */
  ac: number
  /** 完工预算 BAC */
  bac: number
  /** 测量日期 */
  measureDate?: string
  /** 备注 */
  remark?: string
}

/** 项目 EVM 健康仪表盘视图对象 */
export interface EvmDashboardVO {
  /** 立项ID */
  initiationId: number
  /** 测量记录总数 */
  measureCount: number
  /** 平均 CPI */
  avgCpi?: number
  /** 平均 SPI */
  avgSpi?: number
  /** 最新周期 */
  latestPeriod?: string
  /** 最新 CPI */
  latestCpi?: number
  /** 最新 SPI */
  latestSpi?: number
  /** 最新 EAC */
  latestEac?: number
  /** 最新 VAC */
  latestVac?: number
  /** 预警条数 */
  alertCount?: number
  /** 趋势：[{period, cpi, spi, eac, vac}] */
  trend?: Array<{
    /** 周期 */
    period: string
    /** CPI */
    cpi: number
    /** SPI */
    spi: number
    /** EAC */
    eac: number
    /** VAC */
    vac: number
  }>
}
