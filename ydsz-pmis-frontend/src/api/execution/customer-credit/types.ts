export interface CustomerCreditVO {
  id: number
  customerId: number
  customerName?: string
  /** A/B/C/D */
  level?: string
  score: number
  contractCount?: number
  totalContractAmount?: number
  overdueCount?: number
  overdueAmount?: number
  lastAssessDate?: string
  remark?: string
}

export interface CreditAssessmentDTO {
  customerId: number
  customerName?: string
  contractCount?: number
  totalContractAmount?: number
  overdueCount?: number
  overdueAmount?: number
  cooperationYears?: number
  /** ONTIME/OFTEN_LATE/SEVERE_LATE */
  paymentHabit?: string
}
