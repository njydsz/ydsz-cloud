/**
 * @file 商机管理 API 类型定义
 * @description 定义商机（Opportunity）模块的 VO/DTO 类型，供 index.ts 及业务页面消费；
 *              与后端 OpportunityController 的请求/响应结构保持一致。
 * @module api/project/opportunity
 */

/** 商机 VO（视图对象，用于详情与列表展示） */
export interface OpportunityVO {
  /** 商机 ID */
  id: number
  /** 商机编号（唯一） */
  opportunityCode: string
  /** 商机名称 */
  opportunityName: string
  /** 客户 ID */
  customerId: number
  /** 客户名称 */
  customerName?: string
  /** 商务部门 ID */
  businessDeptId?: number
  /** 责任人 ID */
  ownerId: number
  /** 责任人名称 */
  ownerName?: string
  /** 商机分级（如 A/B/C） */
  level?: string
  /** 商机来源 */
  source?: string
  /** 所属行业 */
  industry?: string
  /** 预估金额（元） */
  estimatedAmount?: number
  /** 赢率（百分比数值） */
  winRate?: number
  /** 预计签约日期 */
  expectedSignDate?: string
  /** 预计开始日期 */
  expectedStartDate?: string
  /** 预计结束日期 */
  expectedEndDate?: string
  /** 商机状态 */
  status?: string
  /** 丢单原因 */
  lostReason?: string
  /** 竞争对手 */
  competitor?: string
  /** 备注 */
  remark?: string
  /** 标签（逗号分隔字符串） */
  tags?: string
  /** 租户 ID */
  tenantId?: number
  /** 创建时间 */
  createdAt?: string
  /** 更新时间 */
  updatedAt?: string
}

/** 商机创建 DTO */
export interface OpportunityCreateDTO {
  /** 商机编号（唯一） */
  opportunityCode: string
  /** 商机名称 */
  opportunityName: string
  /** 客户 ID */
  customerId: number
  /** 客户名称 */
  customerName?: string
  /** 商务部门 ID */
  businessDeptId?: number
  /** 责任人 ID */
  ownerId: number
  /** 责任人名称 */
  ownerName?: string
  /** 商机分级（如 A/B/C） */
  level?: string
  /** 商机来源 */
  source?: string
  /** 所属行业 */
  industry?: string
  /** 预估金额（元） */
  estimatedAmount?: number
  /** 预计签约日期 */
  expectedSignDate?: string
  /** 预计开始日期 */
  expectedStartDate?: string
  /** 预计结束日期 */
  expectedEndDate?: string
  /** 商机状态 */
  status?: string
  /** 备注 */
  remark?: string
  /** 标签（逗号分隔字符串） */
  tags?: string
  /** 租户 ID */
  tenantId?: number
}

/** 商机更新 DTO */
export interface OpportunityUpdateDTO {
  /** 商机 ID */
  id: number
  /** 商机名称 */
  opportunityName?: string
  /** 商机分级（如 A/B/C） */
  level?: string
  /** 所属行业 */
  industry?: string
  /** 预估金额（元） */
  estimatedAmount?: number
  /** 赢率（百分比数值） */
  winRate?: number
  /** 预计签约日期 */
  expectedSignDate?: string
  /** 预计开始日期 */
  expectedStartDate?: string
  /** 预计结束日期 */
  expectedEndDate?: string
  /** 竞争对手 */
  competitor?: string
  /** 备注 */
  remark?: string
  /** 标签（逗号分隔字符串） */
  tags?: string
}

/** 商机状态变更 DTO */
export interface OpportunityStatusDTO {
  /** 商机 ID */
  id: number
  /** 目标状态 */
  targetStatus: string
  /** 丢单原因（仅在转为 LOST 状态时填写） */
  lostReason?: string
}
