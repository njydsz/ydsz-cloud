/**
 * @file 可计费利用率 DTO / VO 类型定义
 * @description 定义可计费利用率模块的视图对象（VO）与数据传输对象（DTO），
 *              供 BillableUtilizationController 相关接口出入参使用。
 * @module api/execution/utilization/types
 */

/** 员工利用率明细行视图对象 */
export interface UtilizationRowVO {
  /** 员工ID */
  employeeId?: number
  /** 员工姓名 */
  employeeName?: string
  /** 职级编码 */
  levelCode?: string
  /** 统计周期（YYYY-MM） */
  period?: string
  /** 总工时 */
  totalHours?: number
  /** 可计费工时 */
  billableHours?: number
  /** 加班工时 */
  overtimeHours?: number
  /** 请假工时 */
  leaveHours?: number
  /** 培训工时 */
  trainingHours?: number
  /** 利用率百分比（数值） */
  utilizationPct?: number
  /** 利用率百分比（展示用字符串） */
  utilizationPctDisplay?: string
  /** 评级编码 */
  grade?: string
  /** 评级描述 */
  gradeDesc?: string
  /** 是否预警 */
  alert?: boolean
}

/** 公司/团队整体利用率视图对象 */
export interface UtilizationOverallVO {
  /** 总工时 */
  totalHours?: number
  /** 可计费工时 */
  billableHours?: number
  /** 员工人数 */
  employeeCount?: number
  /** 利用率百分比（数值） */
  utilizationPct?: number
  /** 利用率百分比（展示用字符串） */
  utilizationPctDisplay?: string
  /** 评级编码 */
  grade?: string
  /** 评级描述 */
  gradeDesc?: string
}

/** 利用率快照视图对象 */
export interface UtilizationSnapshotVO {
  /** 统计周期（YYYY-MM） */
  period?: string
  /** 平均利用率（下划线风格旧字段） */
  avg_pct?: number
  /** 平均利用率（驼峰风格新字段） */
  avgPct?: number
  /** 总人数 */
  headcount?: number
  /** 预警人数 */
  warn_count?: number
  /** 危险人数 */
  critical_count?: number
  /** 优秀人数 */
  excellent_count?: number
  /** 良好人数 */
  good_count?: number
  /** 正常人数 */
  normal_count?: number
  /** 总工时合计 */
  sum_total?: number
  /** 可计费工时合计 */
  sum_billable?: number
  /** 闲置工时合计 */
  sum_bench?: number
  /** 数据来源 */
  source?: string
  /** 计算耗时（毫秒） */
  costMs?: number
}

/** 利用率快照重算结果视图对象 */
export interface UtilizationRecomputeVO {
  /** 是否成功 */
  ok?: boolean
  /** 重算周期（YYYY-MM） */
  period?: string
  /** 是否重算所有周期 */
  recomputeAll?: boolean
  /** 受影响记录数 */
  affectedCount?: number
  /** 重算区间起始 */
  rangeFrom?: string
  /** 重算区间结束 */
  rangeTo?: string
  /** 重算时间 */
  recomputeAt?: string
  /** 计算耗时（毫秒） */
  costMs?: number
}
