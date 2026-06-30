export interface WbsTaskVO {
  id: number
  taskCode: string
  taskName: string
  initiationId: number
  initiationName?: string
  parentId?: number
  taskLevel?: number
  wbsPath?: string
  sortOrder?: number
  /** TASK/MILESTONE/SUMMARY */
  taskType?: string
  plannedStartDate?: string
  plannedEndDate?: string
  actualStartDate?: string
  actualEndDate?: string
  durationDays?: number
  plannedEffort?: number
  actualEffort?: number
  progressPct?: number
  ownerId: number
  ownerName?: string
  assigneeIds?: string
  /** LOW/NORMAL/HIGH/URGENT */
  priority?: string
  /** PLANNED/IN_PROGRESS/BLOCKED/IN_REVIEW/COMPLETED/CANCELLED */
  status?: string
  dependsOn?: string
  milestone?: number
  description?: string
  deliverable?: string
  /** LOW/MEDIUM/HIGH */
  riskLevel?: string
}

export interface WbsTaskCreateDTO {
  taskCode: string
  taskName: string
  initiationId: number
  parentId?: number
  taskLevel?: number
  taskType?: string
  plannedStartDate?: string
  plannedEndDate?: string
  plannedEffort?: number
  ownerId: number
  assigneeIds?: string
  priority?: string
  description?: string
  deliverable?: string
  dependsOn?: string
}

export interface WbsTaskStatusDTO {
  id: number
  targetStatus: string
  progressPct?: number
  reason?: string
}
