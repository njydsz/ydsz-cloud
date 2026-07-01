/**
 * @file 预警分发类型定义
 * @description 包含预警分发 VO/DTO、聚合统计 VO、目标角色解析 VO 等类型定义，
 *              供 alert 子模块下的 API 与页面共用。
 * @module api/execution/alert/types
 */

/** 预警分发 VO */
export interface AlertDispatchVO {
  /** 主键 ID */
  id: number
  /** 预警编号 */
  alertCode: string
  /** 预警类型：BUDGET/RISK/EVM/SLA/BENCH/UTILIZATION/QUALITY/OTHER */
  alertType: string
  /** 预警等级：YELLOW/RED/NORMAL */
  alertLevel: string
  /** 来源类型 */
  sourceType?: string
  /** 来源对象 ID */
  sourceId?: string
  /** 预警标题 */
  title: string
  /** 预警内容 */
  content?: string
  /** 目标角色：PM/PMO/GM/CFO/HR/ALL */
  targetRole: string
  /** 目标用户 ID 列表（逗号分隔） */
  targetUserIds?: string
  /** 推送渠道：IN_APP/EMAIL/SMS（逗号分隔） */
  pushChannels: string
  /** 分发时间 */
  dispatchedAt?: string
  /** 分发操作人 */
  dispatchedBy?: string
  /** 分发状态：PENDING/SENT/FAILED/CANCELLED */
  status: string
  /** 发送成功时间 */
  sentAt?: string
  /** 失败原因 */
  failReason?: string
  /** 重试次数 */
  retryCount?: number
  /** 创建时间 */
  createdAt?: string
}

/** 预警提交 DTO */
export interface AlertDispatchDTO {
  /** 预警编号（可选，由系统生成） */
  alertCode?: string
  /** 预警类型 */
  alertType: string
  /** 预警等级 */
  alertLevel: string
  /** 来源类型 */
  sourceType?: string
  /** 来源对象 ID */
  sourceId?: string
  /** 预警标题 */
  title: string
  /** 预警内容 */
  content?: string
  /** 目标角色（可选，未传则按等级自动解析） */
  targetRole?: string
  /** 目标用户 ID 列表（逗号分隔） */
  targetUserIds?: string
  /** 推送渠道：IN_APP/EMAIL/SMS（逗号分隔） */
  pushChannels?: string
  /** 分发操作人 */
  dispatchedBy?: string
}

/** 按类型 × 等级 聚合统计 VO */
export interface AlertAggregateVO {
  /** 预警类型 */
  alertType: string
  /** 预警等级 */
  alertLevel: string
  /** 数量 */
  count: number
}

/** 解析等级对应目标角色 VO */
export interface AlertResolveRolesVO {
  /** 预警等级 */
  level: string
  /** 目标角色集合 */
  roles: string[]
}
