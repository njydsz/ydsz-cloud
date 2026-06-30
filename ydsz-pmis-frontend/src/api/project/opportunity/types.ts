/** 商机 VO */
export interface OpportunityVO {
  id: number
  opportunityCode: string
  opportunityName: string
  customerId: number
  customerName?: string
  businessDeptId?: number
  ownerId: number
  ownerName?: string
  level?: string
  source?: string
  industry?: string
  estimatedAmount?: number
  winRate?: number
  expectedSignDate?: string
  expectedStartDate?: string
  expectedEndDate?: string
  status?: string
  lostReason?: string
  competitor?: string
  remark?: string
  tags?: string
  tenantId?: number
  createdAt?: string
  updatedAt?: string
}

export interface OpportunityCreateDTO {
  opportunityCode: string
  opportunityName: string
  customerId: number
  customerName?: string
  businessDeptId?: number
  ownerId: number
  ownerName?: string
  level?: string
  source?: string
  industry?: string
  estimatedAmount?: number
  expectedSignDate?: string
  expectedStartDate?: string
  expectedEndDate?: string
  status?: string
  remark?: string
  tags?: string
  tenantId?: number
}

export interface OpportunityUpdateDTO {
  id: number
  opportunityName?: string
  level?: string
  industry?: string
  estimatedAmount?: number
  winRate?: number
  expectedSignDate?: string
  expectedStartDate?: string
  expectedEndDate?: string
  competitor?: string
  remark?: string
  tags?: string
}

export interface OpportunityStatusDTO {
  id: number
  targetStatus: string
  lostReason?: string
}
