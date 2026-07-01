/**
 * @file 售后模块类型定义
 * @description 包含质保期、运维工单、满意度评价相关的 VO/DTO 类型定义，
 *              供 aftersales 子模块下的 API 与页面共用。
 * @module api/execution/aftersales/types
 */

/** 质保期 VO */
export interface WarrantyVO {
  /** 主键 ID */
  id: number
  /** 质保期编号 */
  warrantyCode: string
  /** 立项 ID */
  initiationId: number
  /** 立项名称 */
  initiationName?: string
  /** 状态：ACTIVE/EXPIRING_SOON/EXPIRED/TERMINATED */
  status: string
  /** 质保开始日期 YYYY-MM-DD */
  startDate: string
  /** 质保结束日期 YYYY-MM-DD */
  endDate: string
  /** 质保时长（月） */
  durationMonths: number
  /** 到期提前提醒天数 */
  noticeDays: number
  /** 联系人姓名 */
  contactName?: string
  /** 联系人电话 */
  contactPhone?: string
  /** 描述说明 */
  description?: string
  /** 终止原因 */
  terminationReason?: string
  /** 终止时间 */
  terminatedAt?: string
  /** 创建时间 */
  createdAt?: string
}

/** 创建质保期 DTO */
export interface WarrantyCreateDTO {
  /** 立项 ID */
  initiationId: number
  /** 质保时长（月） */
  durationMonths: number
  /** 质保开始日期（可选，默认取当前日期） */
  startDate?: string
  /** 到期提前提醒天数 */
  noticeDays?: number
  /** 联系人姓名 */
  contactName?: string
  /** 联系人电话 */
  contactPhone?: string
  /** 描述说明 */
  description?: string
}

/** 终止质保期 DTO */
export interface WarrantyTerminateDTO {
  /** 质保期 ID */
  id: number
  /** 终止原因 */
  reason: string
}

/** 运维工单 VO */
export interface OpsTicketVO {
  /** 主键 ID */
  id: number
  /** 工单编号 */
  ticketCode: string
  /** 立项 ID */
  initiationId?: number
  /** 立项名称 */
  initiationName?: string
  /** 关联质保期 ID */
  warrantyId?: number
  /** 关联质保期编号 */
  warrantyCode?: string
  /** 工单标题 */
  title: string
  /** 工单描述 */
  description?: string
  /** 状态：OPEN/ASSIGNED/IN_PROGRESS/RESOLVED/CLOSED/CANCELLED */
  status: string
  /** 优先级：P1/P2/P3/P4 */
  priority: string
  /** 报告人 ID */
  reporterId?: number
  /** 报告人姓名 */
  reporterName?: string
  /** 当前处理人 ID */
  assigneeId?: number
  /** 当前处理人姓名 */
  assigneeName?: string
  /** 响应截止时间 */
  responseDueAt?: string
  /** 解决截止时间 */
  resolveDueAt?: string
  /** 实际响应时间 */
  respondedAt?: string
  /** 实际解决时间 */
  resolvedAt?: string
  /** 关闭时间 */
  closedAt?: string
  /** 响应是否超 SLA */
  responseSlaBreached?: boolean
  /** 解决是否超 SLA */
  resolveSlaBreached?: boolean
  /** 客户满意度评分 */
  satisfactionScore?: number
  /** 客户满意度评语 */
  satisfactionComment?: string
  /** 创建时间 */
  createdAt?: string
}

/** 工单创建 DTO */
export interface OpsTicketCreateDTO {
  /** 立项 ID */
  initiationId?: number
  /** 关联质保期 ID */
  warrantyId?: number
  /** 工单标题 */
  title: string
  /** 工单描述 */
  description?: string
  /** 工单分类：BUG/DATA/CONFIG/PROCESS/OTHER */
  category?: string
  /** 优先级：P1/P2/P3/P4 */
  priority: string
  /** 报告人 ID */
  reporterId?: number
  /** 报告人姓名 */
  reporterName?: string
  /** 报告人电话 */
  reporterPhone?: string
  /** 附件 ID 列表（逗号分隔） */
  fileIds?: string
}

/** 工单派单 DTO */
export interface OpsTicketAssignDTO {
  /** 工单 ID */
  id: number
  /** 指派处理人 ID */
  assigneeId: number
  /** 派单备注 */
  comment?: string
}

/** 工单状态变更 DTO */
export interface OpsTicketStatusDTO {
  /** 工单 ID */
  id: number
  /** 目标状态 */
  targetStatus: string
  /** 解决说明 */
  resolutionNote?: string
  /** 客户评分 */
  customerScore?: number
  /** 客户评论 */
  customerComment?: string
  /** 处理备注 */
  comment?: string
}

/** SLA 达成率统计 VO */
export interface OpsTicketSlaSummaryVO {
  /** 优先级 */
  priority: string
  /** 总数 */
  totalCount: number
  /** 响应超时数量 */
  responseBreachCount: number
  /** 解决超时数量 */
  resolveBreachCount: number
  /** 响应 SLA 达成率 */
  responseSlaRate: number
  /** 解决 SLA 达成率 */
  resolveSlaRate: number
}

/** 满意度 VO */
export interface SatisfactionVO {
  /** 主键 ID */
  id: number
  /** 满意度评价编号 */
  satisfactionCode: string
  /** 关联工单 ID */
  ticketId?: number
  /** 关联工单编号 */
  ticketCode?: string
  /** 立项 ID */
  initiationId?: number
  /** 立项名称 */
  initiationName?: string
  /** 评价人 ID */
  evaluatorId?: number
  /** 评价人姓名 */
  evaluatorName?: string
  /** 整体评分 1-5 */
  overallScore: number
  /** 响应速度评分 1-5 */
  responseScore?: number
  /** 专业能力评分 1-5 */
  professionalScore?: number
  /** 服务态度评分 1-5 */
  attitudeScore?: number
  /** 结果质量评分 1-5 */
  resultScore?: number
  /** 处理速度评分 1-5 */
  speedScore?: number
  /** 评价意见 */
  comment?: string
  /** 跟进状态：PENDING/FOLLOW_UP/CLOSED */
  followUpStatus?: string
  /** 跟进备注 */
  followUpNote?: string
  /** 跟进时间 */
  followUpAt?: string
  /** 创建时间 */
  createdAt?: string
}

/** 满意度创建 DTO */
export interface SatisfactionCreateDTO {
  /** 关联工单 ID */
  ticketId?: number
  /** 立项 ID */
  initiationId?: number
  /** 评价人 ID */
  evaluatorId?: number
  /** 整体评分 1-5 */
  overallScore: number
  /** 响应速度评分 1-5 */
  responseScore?: number
  /** 专业能力评分 1-5 */
  professionalScore?: number
  /** 服务态度评分 1-5 */
  attitudeScore?: number
  /** 结果质量评分 1-5 */
  resultScore?: number
  /** 处理速度评分 1-5 */
  speedScore?: number
  /** 评价意见 */
  comment?: string
}
