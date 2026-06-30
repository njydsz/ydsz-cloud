/**
 * EVM 测量相关 VO / DTO
 */
export interface EvmMeasureVO {
  id?: number
  initiationId: number
  wbsTaskId?: number
  period: string
  pv: number
  ev: number
  ac: number
  bac: number
  cpi?: number
  spi?: number
  cv?: number
  sv?: number
  eac?: number
  vac?: number
  etc?: number
  tcpi?: number
  alertLevel?: 'GREEN' | 'YELLOW' | 'RED'
  alertReason?: string
  measureDate?: string
  remark?: string
}

export interface EvmMeasureCreateDTO {
  initiationId: number
  wbsTaskId?: number
  period: string
  pv: number
  ev: number
  ac: number
  bac: number
  measureDate?: string
  remark?: string
}

export interface EvmDashboardVO {
  initiationId: number
  measureCount: number
  avgCpi?: number
  avgSpi?: number
  latestPeriod?: string
  latestCpi?: number
  latestSpi?: number
  latestEac?: number
  latestVac?: number
  alertCount?: number
  /** 趋势：[{period, cpi, spi, eac, vac}] */
  trend?: Array<{
    period: string
    cpi: number
    spi: number
    eac: number
    vac: number
  }>
}
