/** 质保期 VO */
export interface WarrantyVO {
  id: number
  warrantyCode: string
  initiationId: number
  initiationName?: string
  /** ACTIVE/EXPIRING_SOON/EXPIRED/TERMINATED */
  status: string
  startDate: string
  endDate: string
  durationMonths: number
  noticeDays: number
  contactName?: string
  contactPhone?: string
  description?: string
  terminationReason?: string
  terminatedAt?: string
  createdAt?: string
}

/** 创建 DTO */
export interface WarrantyCreateDTO {
  initiationId: number
  durationMonths: number
  startDate?: string
  noticeDays?: number
  contactName?: string
  contactPhone?: string
  description?: string
}

/** 终止 DTO */
export interface WarrantyTerminateDTO {
  id: number
  reason: string
}

/** 运维工单 VO */
export interface OpsTicketVO {
  id: number
  ticketCode: string
  initiationId?: number
  initiationName?: string
  warrantyId?: number
  warrantyCode?: string
  title: string
  description?: string
  /** OPEN/ASSIGNED/IN_PROGRESS/RESOLVED/CLOSED/CANCELLED */
  status: string
  /** P1/P2/P3/P4 */
  priority: string
  reporterId?: number
  reporterName?: string
  assigneeId?: number
  assigneeName?: string
  responseDueAt?: string
  resolveDueAt?: string
  respondedAt?: string
  resolvedAt?: string
  closedAt?: string
  responseSlaBreached?: boolean
  resolveSlaBreached?: boolean
  satisfactionScore?: number
  satisfactionComment?: string
  createdAt?: string
}

/** 工单创建 DTO */
export interface OpsTicketCreateDTO {
  initiationId?: number
  warrantyId?: number
  title: string
  description?: string
  /** BUG/DATA/CONFIG/PROCESS/OTHER */
  category?: string
  /** P1/P2/P3/P4 */
  priority: string
  reporterId?: number
  reporterName?: string
  reporterPhone?: string
  fileIds?: string
}

/** 工单派单 DTO */
export interface OpsTicketAssignDTO {
  id: number
  assigneeId: number
  comment?: string
}

/** 工单状态变更 DTO */
export interface OpsTicketStatusDTO {
  id: number
  targetStatus: string
  resolutionNote?: string
  customerScore?: number
  customerComment?: string
  comment?: string
}

/** SLA 达成率统计 */
export interface OpsTicketSlaSummaryVO {
  priority: string
  totalCount: number
  responseBreachCount: number
  resolveBreachCount: number
  responseSlaRate: number
  resolveSlaRate: number
}

/** 满意度 VO */
export interface SatisfactionVO {
  id: number
  satisfactionCode: string
  ticketId?: number
  ticketCode?: string
  initiationId?: number
  initiationName?: string
  evaluatorId?: number
  evaluatorName?: string
  /** 1-5 */
  overallScore: number
  /** 1-5 维度评分 */
  responseScore?: number
  professionalScore?: number
  attitudeScore?: number
  resultScore?: number
  speedScore?: number
  comment?: string
  /** PENDING/FOLLOW_UP/CLOSED */
  followUpStatus?: string
  followUpNote?: string
  followUpAt?: string
  createdAt?: string
}

/** 满意度创建 DTO */
export interface SatisfactionCreateDTO {
  ticketId?: number
  initiationId?: number
  evaluatorId?: number
  overallScore: number
  responseScore?: number
  professionalScore?: number
  attitudeScore?: number
  resultScore?: number
  speedScore?: number
  comment?: string
}
