/**
 * @file Bench 闲置池 类型定义
 * @description 定义 Bench 闲置记录（BenchRecord）相关的 VO、DTO 与仪表盘 VO 类型，供 bench/index.ts 及上层业务使用。
 * @module api/resource/bench
 */
export interface BenchRecordVO {
  /** Bench 记录 ID */
  id?: number
  /** Bench 编码 */
  benchCode: string
  /** 员工 ID */
  employeeId: number
  /** 员工姓名 */
  employeeName?: string
  /** 职级编码（如 L1-L18） */
  levelCode?: string
  /** 所属资源池 ID */
  poolId?: number
  /** 所属资源池名称 */
  poolName?: string
  /** Bench 动作：ENTER / EXIT */
  benchReason?: string
  /** 入池原因类型：PROJECT_END / RESERVE / TRAINING / LEAVE */
  reasonType?: string
  /** 来源分配记录 ID（对应后端 Long 类型） */
  sourceAssignment?: number
  /** 入池日期 */
  benchDate: string
  /** 出池日期 */
  exitDate?: string
  /** 闲置天数 */
  idleDays?: number
  /** 状态：ACTIVE / EXITED */
  status?: string
  /** 日均闲置成本 */
  dailyCost?: number
  /** 累计闲置成本 */
  totalIdleCost?: number
  /** 备注 */
  remark?: string
}

export interface BenchRecordCreateDTO {
  /** Bench 编码 */
  benchCode: string
  /** 员工 ID */
  employeeId: number
  /** 员工姓名 */
  employeeName?: string
  /** 职级编码（如 L1-L18） */
  levelCode?: string
  /** 所属资源池 ID */
  poolId?: number
  /** Bench 动作：ENTER / EXIT */
  action: string
  /** 入池原因类型：PROJECT_END / RESERVE / TRAINING / LEAVE */
  reasonType?: string
  /** 来源分配记录 ID（对应后端 Long 类型） */
  sourceAssignment?: number
  /** 入池日期 */
  benchDate: string
  /** 出池日期 */
  exitDate?: string
  /** 日均闲置成本 */
  dailyCost?: number
  /** 备注 */
  remark?: string
}

export interface BenchDashboardVO {
  /** 活跃资源池列表 */
  activePools: Array<Record<string, unknown>>
  /** 累计闲置成本 */
  totalIdleCost?: number
  /** 当前在池人数 */
  activeCount?: number
  /** 已出池人数 */
  exitedCount?: number
  /** 流动统计列表 */
  flow?: Array<Record<string, unknown>>
}
