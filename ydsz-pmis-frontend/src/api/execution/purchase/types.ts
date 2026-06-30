export interface PurchaseVO {
  id: number
  purchaseCode: string
  initiationId: number
  initiationName?: string
  vendor?: string
  itemName: string
  quantity?: number
  unitPrice?: number
  amount?: number
  purchaseDate?: string
  /** DRAFT/SUBMITTED/APPROVED/REJECTED/RECEIVED/PAID/CANCELLED */
  status?: string
  applicantId?: number
  applicantName?: string
  approverId?: number
  approverName?: string
  approvedAt?: string
  description?: string
  createdAt?: string
}

export interface PurchaseCreateDTO {
  purchaseCode: string
  initiationId: number
  vendor?: string
  itemName: string
  quantity?: number
  unitPrice?: number
  amount?: number
  purchaseDate?: string
  applicantId: number
  description?: string
}

export interface ApprovalDTO {
  id: number
  targetStatus: string
  approverId?: number
  approverName?: string
  reason?: string
}
