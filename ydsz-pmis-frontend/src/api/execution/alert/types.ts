/** 预警分发 VO */
export interface AlertDispatchVO {
  id: number
  alertCode: string
  /** BUDGET/RISK/EVM/SLA/BENCH/UTILIZATION/QUALITY/OTHER */
  alertType: string
  /** YELLOW/RED/NORMAL */
  alertLevel: string
  sourceType?: string
  sourceId?: string
  title: string
  content?: string
  /** PM/PMO/GM/CFO/HR/ALL */
  targetRole: string
  targetUserIds?: string
  /** IN_APP/EMAIL/SMS 逗号分隔 */
  pushChannels: string
  dispatchedAt?: string
  dispatchedBy?: string
  /** PENDING/SENT/FAILED/CANCELLED */
  status: string
  sentAt?: string
  failReason?: string
  retryCount?: number
  createdAt?: string
}

/** 预警提交 DTO */
export interface AlertDispatchDTO {
  alertCode?: string
  alertType: string
  alertLevel: string
  sourceType?: string
  sourceId?: string
  title: string
  content?: string
  targetRole?: string
  targetUserIds?: string
  pushChannels?: string
  dispatchedBy?: string
}

/** 按类型 × 等级 聚合统计 */
export interface AlertAggregateVO {
  alertType: string
  alertLevel: string
  count: number
}

/** 解析等级对应目标角色 */
export interface AlertResolveRolesVO {
  level: string
  roles: string[]
}
