/** 合同主表 */
export interface ContractVO {
  id: number
  contractCode: string
  contractName: string
  initiationId?: number
  initiationName?: string
  customerId: number
  customerName?: string
  contractType?: string
  amount?: number
  currency?: string
  signDate?: string
  effectiveDate?: string
  expireDate?: string
  paymentTerms?: string
  /** DRAFT/UNDER_REVIEW/APPROVED/SIGNED/EXECUTING/CLOSED/REJECTED */
  status?: string
  ownerId?: number
  ownerName?: string
  templateId?: number
  riskLevel?: string
  riskScore?: number
  description?: string
  tenantId?: number
  createdAt?: string
  updatedAt?: string
}

export interface ContractCreateDTO {
  contractCode: string
  contractName: string
  initiationId?: number
  customerId: number
  customerName?: string
  contractType: string
  amount: number
  currency?: string
  signDate?: string
  effectiveDate?: string
  expireDate?: string
  paymentTerms?: string
  ownerId?: number
  templateId?: number
  description?: string
}

export interface ContractStatusDTO {
  id: number
  targetStatus: string
  reason?: string
}

/** 合同模板 */
export interface ContractTemplateVO {
  id: number
  code: string
  name: string
  /** FIXED_PRICE/T&M/MILESTONE/RETAINER/LICENSE/SaaS/MAINTENANCE/OTHER */
  type?: string
  version?: string
  content?: string
  /** DRAFT/PUBLISHED/DEPRECATED */
  status?: string
  description?: string
  createdBy?: number
  createdAt?: string
}

export interface ContractTemplateCreateDTO {
  code: string
  name: string
  type: string
  version?: string
  content: string
  description?: string
}

export interface ContractTemplateStatusDTO {
  id: number
  targetStatus: string
}

/** 合同变更 */
export interface ContractChangeVO {
  id: number
  contractId: number
  contractCode?: string
  /** SCOPE/COST/TERM/STAFF/SCHEDULE/OTHER */
  changeType?: string
  /** LOW/MEDIUM/HIGH */
  impactLevel?: string
  impactScore?: number
  reason?: string
  beforeValue?: string
  afterValue?: string
  /** DRAFT/SUBMITTED/UNDER_REVIEW/APPROVED/REJECTED/CLOSED/CANCELLED */
  status?: string
  applicantId?: number
  applicantName?: string
  approverId?: number
  approverName?: string
  createdAt?: string
}

export interface ContractChangeCreateDTO {
  contractId: number
  changeType: string
  reason: string
  beforeValue?: string
  afterValue?: string
  description?: string
}

export interface ContractChangeStatusDTO {
  id: number
  targetStatus: string
  reason?: string
}
