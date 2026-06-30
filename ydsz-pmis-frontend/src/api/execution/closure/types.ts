export interface ProjectClosureVO {
  id: number
  closureCode: string
  initiationId: number
  initiationName?: string
  /** FORMAL/PRE_CLOSURE/FORCED */
  type?: string
  /** DRAFT/SUBMITTED/APPROVED/REJECTED/ARCHIVED */
  status?: string
  reason?: string
  summary?: string
  lessonsLearned?: string
  warrantyEndDate?: string
  paymentRatio?: number
  grossMargin?: number
  applicantId?: number
  applicantName?: string
  approverId?: number
  approverName?: string
  createdAt?: string
}

export interface ProjectClosureCreateDTO {
  closureCode: string
  initiationId: number
  type: string
  reason?: string
  summary?: string
  lessonsLearned?: string
  warrantyEndDate?: string
}

export interface ProjectClosureStatusDTO {
  id: number
  targetStatus: string
  reason?: string
}
