export interface ExpenseVO {
  id: number
  expenseCode: string
  initiationId?: number
  initiationName?: string
  employeeId: number
  employeeName?: string
  expenseType?: string
  amount: number
  expenseDate?: string
  description?: string
  receiptUrl?: string
  /** DRAFT/SUBMITTED/APPROVED/REJECTED/PAID/CANCELLED */
  status?: string
  approverId?: number
  approverName?: string
  approvedAt?: string
  createdAt?: string
}

export interface ExpenseCreateDTO {
  expenseCode: string
  initiationId?: number
  employeeId: number
  expenseType?: string
  amount: number
  expenseDate?: string
  description?: string
  receiptUrl?: string
}

export interface ApprovalDTO {
  id: number
  targetStatus: string
  approverId?: number
  approverName?: string
  reason?: string
}
