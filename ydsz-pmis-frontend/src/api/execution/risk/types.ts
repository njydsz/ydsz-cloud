export interface RiskVO {
  id: number
  initiationId: number
  initiationName?: string
  riskCode?: string
  riskName: string
  description?: string
  /** TECHNICAL/COMMERCE/RESOURCE/EXTERNAL/OTHER */
  category?: string
  probability?: number
  impact?: number
  riskScore?: number
  /** LOW/MEDIUM/HIGH */
  level?: string
  ownerId?: number
  ownerName?: string
  mitigation?: string
  /** OPEN/MITIGATING/CLOSED/ACCEPTED */
  status?: string
  createdAt?: string
}

export interface RiskCreateDTO {
  initiationId: number
  riskCode?: string
  riskName: string
  description?: string
  category?: string
  probability?: number
  impact?: number
  ownerId?: number
  mitigation?: string
}

export interface RiskStatusDTO {
  id: number
  targetStatus: string
  reason?: string
}
