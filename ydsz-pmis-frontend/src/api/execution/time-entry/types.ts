export interface TimeEntryVO {
  id: number
  entryDate: string
  employeeId: number
  employeeName?: string
  levelCode?: string
  initiationId: number
  initiationName?: string
  taskId?: number
  taskName?: string
  hours: number
  days?: number
  overtime?: number
  /** REGULAR/OVERTIME/TRAINING/LEAVE */
  workType?: string
  description?: string
  /** DRAFT/SUBMITTED/APPROVED/REJECTED */
  status?: string
  approverId?: number
  approverName?: string
  approvedAt?: string
  rejectReason?: string
  createdAt?: string
}

export interface TimeEntryCreateDTO {
  entryDate: string
  employeeId: number
  levelCode?: string
  initiationId: number
  taskId?: number
  hours: number
  overtime?: number
  workType?: string
  description?: string
}

export interface TimeEntryApprovalDTO {
  id: number
  approverId: number
  approverName?: string
  reason?: string
}
