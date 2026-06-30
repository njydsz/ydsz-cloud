export interface RevenueVO {
  id: number
  initiationId: number
  initiationName?: string
  contractId?: number
  contractCode?: string
  /** FINAL/MILESTONE/MONTHLY */
  recognitionMethod?: string
  amount: number
  period: string
  recognitionDate?: string
  description?: string
  status?: string
  createdAt?: string
}

export interface RevenueCreateDTO {
  initiationId: number
  contractId?: number
  recognitionMethod: string
  amount: number
  period: string
  recognitionDate?: string
  description?: string
}

export interface ProfitSnapshotVO {
  id: number
  initiationId: number
  initiationName?: string
  period: string
  revenue: number
  laborCost: number
  purchaseCost: number
  expenseCost: number
  allocatedCost: number
  totalCost: number
  grossProfit: number
  grossMargin: number
  eac?: number
  healthScore?: number
  createdAt?: string
}
