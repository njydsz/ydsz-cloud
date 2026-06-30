export interface PaymentVO {
  id: number
  paymentCode: string
  customerId: number
  customerName?: string
  initiationId: number
  initiationName?: string
  contractId?: number
  amount: number
  paymentMethod?: string
  paymentDate?: string
  /** PENDING/CONFIRMED/ALLOCATED/CANCELLED */
  status?: string
  unallocatedAmount?: number
  bankAccount?: string
  bankRef?: string
  remark?: string
  createdAt?: string
}

export interface PaymentCreateDTO {
  paymentCode: string
  customerId: number
  customerName?: string
  initiationId: number
  contractId?: number
  amount: number
  paymentMethod?: string
  paymentDate?: string
  bankAccount?: string
  bankRef?: string
  remark?: string
}

export interface PaymentAllocationDTO {
  paymentId: number
  invoiceId: number
  amount: number
}

/** 回款核销明细 */
export interface PaymentAllocationVO {
  id: number
  paymentId: number
  invoiceId: number
  invoiceCode?: string
  amount: number
  allocatedAt?: string
}
