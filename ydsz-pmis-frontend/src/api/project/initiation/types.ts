/** 立项 VO */
export interface InitiationVO {
  id: number
  projectCode: string
  projectName: string
  opportunityId?: number
  customerId: number
  customerName?: string
  businessDeptId?: number
  projectType?: string
  projectLevel?: string
  pmId?: number
  pmName?: string
  sponsorId?: number
  sponsorName?: string
  estimatedAmount?: number
  budgetAmount?: number
  plannedStartDate?: string
  plannedEndDate?: string
  durationDays?: number
  stage?: string
  currentGate?: string
  description?: string
  businessCase?: string
  riskAssessment?: string
  workflowId?: string
  tenantId?: number
  createdAt?: string
  updatedAt?: string
}

export interface InitiationCreateDTO {
  projectCode: string
  projectName: string
  opportunityId?: number
  customerId: number
  customerName?: string
  businessDeptId?: number
  projectType: string
  projectLevel?: string
  pmId?: number
  pmName?: string
  sponsorId?: number
  sponsorName?: string
  estimatedAmount?: number
  budgetAmount?: number
  plannedStartDate?: string
  plannedEndDate?: string
  description?: string
  businessCase?: string
  riskAssessment?: string
}

export interface InitiationStageDTO {
  id: number
  targetStage: string
  gate?: string
}

export interface BudgetItemDTO {
  initiationId: number
  category: string
  itemName: string
  amount: number
  remark?: string
}

export interface BudgetItemVO {
  id: number
  initiationId: number
  category: string
  itemName: string
  amount: number
  remark?: string
  createdAt?: string
}

export interface GateReviewDTO {
  initiationId: number
  /** CD1_KICKOFF/CD2_DESIGN/CD3_BUILD/CD4_UAT/CD5_GO_LIVE */
  gateCode: string
  /** PASS/FAIL/CONDITIONAL */
  reviewResult: string
  reviewerId?: number
  reviewerName?: string
  comment?: string
}

export interface GateReviewVO {
  id: number
  initiationId: number
  gateCode: string
  reviewResult: string
  reviewerId?: number
  reviewerName?: string
  comment?: string
  reviewedAt?: string
}
