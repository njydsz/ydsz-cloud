/**
 * @file 利润测算 VO / DTO 类型定义
 * @description 定义利润测算模块的视图对象（VO）与数据传输对象（DTO），
 *              供 ProfitSimulationController 相关接口出入参使用。
 * @module api/finance/profit-simulation/types
 */

/** 利润测算方案视图对象 */
export interface ProfitSimulationVO {
  /** 测算方案ID */
  id?: string
  /** 测算方案编码 */
  simulationCode: string
  /** 测算方案名称 */
  simulationName: string
  /** 立项ID */
  initiationId: number
  /** V1/V2/V3... */
  version?: number
  /** BASE/OPTIMISTIC/PESSIMISTIC/CUSTOM */
  scenarioType?: string
  /** 合同金额 */
  contractAmount: number
  /** 外部收入 */
  externalRevenue?: number
  /** 内部成本 */
  internalCost?: number
  /** 预计工时 */
  expectedHours?: number
  /** 混合费率 */
  blendedRate?: number
  /** 毛利 */
  grossProfit?: number
  /** 毛利率 */
  grossMargin?: number
  /** 目标毛利率 */
  targetMargin?: number
  /** 人力成本 */
  laborCost?: number
  /** 采购成本 */
  purchaseCost?: number
  /** 费用成本 */
  expenseCost?: number
  /** 外包成本 */
  outsourceCost?: number
  /** 假设说明 */
  assumptions?: string
  /** DRAFT/SUBMITTED/APPROVED/REJECTED */
  status?: string
  /** 审批人姓名 */
  approverName?: string
  /** 审批时间 */
  approvedAt?: string
  /** 备注 */
  remark?: string
  /** 申请人ID */
  applicantId?: number
  /** 申请人姓名 */
  applicantName?: string
}

/** 利润测算方案创建 DTO */
export interface ProfitSimulationCreateDTO {
  /** 测算方案编码 */
  simulationCode: string
  /** 测算方案名称 */
  simulationName: string
  /** 立项ID */
  initiationId: number
  /** 场景类型：BASE/OPTIMISTIC/PESSIMISTIC/CUSTOM */
  scenarioType?: string
  /** 合同金额 */
  contractAmount: number
  /** 假设说明 */
  assumptions?: string
  /** 目标毛利率 */
  targetMargin: number
  /** 备注 */
  remark?: string
  /** 申请人ID */
  applicantId?: number
  /** 申请人姓名 */
  applicantName?: string
}

/** 利润测算方案状态变更 DTO */
export interface SimulationStatusDTO {
  /** 测算方案ID */
  id: string
  /** DRAFT/SUBMITTED/APPROVED/REJECTED */
  targetStatus: string
  /** 审批意见 */
  approvalComment?: string
  /** 审批人姓名 */
  approverName?: string
}
