export interface ProfitSimulationVO {
  id?: number
  simulationCode: string
  simulationName: string
  initiationId: number
  /** V1/V2/V3... */
  version?: number
  /** BASE/OPTIMISTIC/PESSIMISTIC/CUSTOM */
  scenarioType?: string
  contractAmount: number
  externalRevenue?: number
  internalCost?: number
  expectedHours?: number
  blendedRate?: number
  grossProfit?: number
  grossMargin?: number
  targetMargin?: number
  laborCost?: number
  purchaseCost?: number
  expenseCost?: number
  outsourceCost?: number
  assumptions?: string
  /** DRAFT/SUBMITTED/APPROVED/REJECTED */
  status?: string
  approverName?: string
  approvedAt?: string
  remark?: string
  applicantId?: number
  applicantName?: string
}

export interface ProfitSimulationCreateDTO {
  simulationCode: string
  simulationName: string
  initiationId: number
  scenarioType?: string
  contractAmount: number
  assumptions?: string
  targetMargin: number
  remark?: string
  applicantId?: number
  applicantName?: string
}

export interface SimulationStatusDTO {
  id: number
  /** DRAFT/SUBMITTED/APPROVED/REJECTED */
  targetStatus: string
  /** 审批意见 */
  approvalComment?: string
  approverName?: string
}
