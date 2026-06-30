export interface BenchRecordVO {
  id?: number
  benchCode: string
  employeeId: number
  employeeName?: string
  levelCode?: string
  poolId?: number
  poolName?: string
  /** ENTER / EXIT */
  benchReason?: string
  /** PROJECT_END / RESERVE / TRAINING / LEAVE */
  reasonType?: string
  sourceAssignment?: number
  benchDate: string
  exitDate?: string
  idleDays?: number
  /** ACTIVE / EXITED */
  status?: string
  dailyCost?: number
  totalIdleCost?: number
  remark?: string
}

export interface BenchRecordCreateDTO {
  benchCode: string
  employeeId: number
  employeeName?: string
  levelCode?: string
  poolId?: number
  /** ENTER / EXIT */
  action: string
  /** PROJECT_END / RESERVE / TRAINING / LEAVE */
  reasonType?: string
  sourceAssignment?: number
  benchDate: string
  exitDate?: string
  dailyCost?: number
  remark?: string
}

export interface BenchDashboardVO {
  activePools: Array<Record<string, any>>
  totalIdleCost?: number
  activeCount?: number
  exitedCount?: number
  flow?: Array<Record<string, any>>
}
