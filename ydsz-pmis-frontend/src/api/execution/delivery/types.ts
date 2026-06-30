export interface DeliveryItemVO {
  id: number
  initiationId: number
  initiationName?: string
  /** CD1_KICKOFF/CD2_DESIGN/CD3_BUILD/CD4_UAT/CD5_GO_LIVE */
  stage?: string
  /** STANDARD/SPECIFIC */
  type?: string
  name: string
  description?: string
  level?: string
  /** PENDING/SUBMITTED/ACCEPTED/REJECTED/WAIVED */
  status?: string
  ownerId?: number
  ownerName?: string
  submittedAt?: string
  acceptedAt?: string
  rejectReason?: string
}

export interface DeliveryItemCreateDTO {
  initiationId: number
  stage: string
  type?: string
  name: string
  description?: string
  level?: string
  ownerId?: number
}

export interface DeliveryItemStatusDTO {
  id: number
  targetStatus: string
  reason?: string
}
