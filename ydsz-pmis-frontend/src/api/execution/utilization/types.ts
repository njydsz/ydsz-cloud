/**
 * 可计费利用率 DTO / VO 类型
 */

export interface UtilizationRowVO {
  employeeId?: number
  employeeName?: string
  levelCode?: string
  period?: string
  totalHours?: number
  billableHours?: number
  overtimeHours?: number
  leaveHours?: number
  trainingHours?: number
  utilizationPct?: number
  utilizationPctDisplay?: string
  grade?: string
  gradeDesc?: string
  alert?: boolean
}

export interface UtilizationOverallVO {
  totalHours?: number
  billableHours?: number
  employeeCount?: number
  utilizationPct?: number
  utilizationPctDisplay?: string
  grade?: string
  gradeDesc?: string
}

export interface UtilizationSnapshotVO {
  period?: string
  avg_pct?: number
  avgPct?: number
  headcount?: number
  warn_count?: number
  critical_count?: number
  excellent_count?: number
  good_count?: number
  normal_count?: number
  sum_total?: number
  sum_billable?: number
  sum_bench?: number
  source?: string
  costMs?: number
}

export interface UtilizationRecomputeVO {
  ok?: boolean
  period?: string
  recomputeAll?: boolean
  affectedCount?: number
  rangeFrom?: string
  rangeTo?: string
  recomputeAt?: string
  costMs?: number
}
