/**
 * @file 风险管理类型定义
 * @description 定义风险的视图对象、创建 DTO 及状态变更 DTO 等数据结构，
 *              供 api/execution/risk 模块及业务页面使用。
 * @module api/execution/risk/types
 */

export interface RiskVO {
  /** 风险 ID */
  id: number
  /** 所属立项 ID */
  initiationId: number
  /** 所属立项名称 */
  initiationName?: string
  /** 风险编码 */
  riskCode?: string
  /** 风险名称 */
  riskName: string
  /** 风险描述 */
  description?: string
  /** 风险类别：TECHNICAL/COMMERCE/RESOURCE/EXTERNAL/OTHER */
  category?: string
  /** 发生概率（1-5） */
  probability?: number
  /** 影响程度（1-5） */
  impact?: number
  /** 风险评分（概率 × 影响） */
  riskScore?: number
  /** 风险等级：LOW/MEDIUM/HIGH */
  level?: string
  /** 风险负责人 ID */
  ownerId?: number
  /** 风险负责人姓名 */
  ownerName?: string
  /** 风险缓解措施 */
  mitigation?: string
  /** 状态：OPEN/MITIGATING/CLOSED/ACCEPTED */
  status?: string
  /** 创建时间 */
  createdAt?: string
}

export interface RiskCreateDTO {
  /** 所属立项 ID */
  initiationId: number
  /** 风险编码 */
  riskCode?: string
  /** 风险名称 */
  riskName: string
  /** 风险描述 */
  description?: string
  /** 风险类别 */
  category?: string
  /** 发生概率（1-5） */
  probability?: number
  /** 影响程度（1-5） */
  impact?: number
  /** 风险负责人 ID */
  ownerId?: number
  /** 风险缓解措施 */
  mitigation?: string
}

export interface RiskStatusDTO {
  /** 风险 ID */
  id: number
  /** 目标状态 */
  targetStatus: string
  /** 状态变更原因/备注 */
  reason?: string
}
