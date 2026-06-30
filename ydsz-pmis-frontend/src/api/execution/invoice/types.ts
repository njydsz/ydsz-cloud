export interface InvoiceVO {
  id: number
  invoiceCode: string
  invoiceNo?: string
  invoiceType?: string
  /** MILESTONE/OUTSOURCING/MONTHLY/FINAL/OTHER */
  invoiceBasis?: string
  customerId: number
  customerName?: string
  initiationId: number
  initiationName?: string
  contractId?: number
  contractCode?: string
  amount: number
  taxRate?: number
  taxAmount?: number
  /** DRAFT/SUBMITTED/APPROVED/REJECTED/ISSUED/RED_REVERSED/CANCELLED */
  status?: string
  issueDate?: string
  dueDate?: string
  reversedById?: number
  remark?: string
  createdAt?: string
}

export interface InvoiceCreateDTO {
  invoiceCode: string
  invoiceType: string
  invoiceBasis: string
  customerId: number
  customerName?: string
  initiationId: number
  contractId?: number
  amount: number
  taxRate?: number
  taxAmount?: number
  dueDate?: string
  description?: string
  /** MILESTONE 必填: 验收证明 URL */
  acceptanceProof?: string
  /** OUTSOURCING 必填: 人天确认单 URL */
  personDaySheet?: string
}

export interface InvoiceApprovalDTO {
  id: number
  approverId?: number
  approverName?: string
  reason?: string
  reversedById?: number
}
