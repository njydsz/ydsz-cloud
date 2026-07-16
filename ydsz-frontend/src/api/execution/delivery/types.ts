/**
 * @file 交付物 VO / DTO 类型定义
 * @description 定义项目交付物模块的视图对象（VO）与数据传输对象（DTO），
 *              供 DeliveryItemController 相关接口出入参使用。
 * @module api/execution/delivery/types
 */

/** 交付物视图对象 */
export interface DeliveryItemVO {
  /** 交付物ID */
  id: number
  /** 立项ID */
  initiationId: number
  /** 立项名称 */
  initiationName?: string
  /** CD1_KICKOFF/CD2_DESIGN/CD3_BUILD/CD4_UAT/CD5_GO_LIVE */
  stage?: string
  /** STANDARD/SPECIFIC */
  type?: string
  /** 交付物名称 */
  name: string
  /** 交付物描述 */
  description?: string
  /** 交付物层级 */
  level?: string
  /** PENDING/SUBMITTED/ACCEPTED/REJECTED/WAIVED */
  status?: string
  /** 责任人ID */
  ownerId?: number
  /** 责任人姓名 */
  ownerName?: string
  /** 提交时间 */
  submittedAt?: string
  /** 验收通过时间 */
  acceptedAt?: string
  /** 驳回原因 */
  rejectReason?: string
}

/** 交付物创建 DTO */
export interface DeliveryItemCreateDTO {
  /** 立项ID */
  initiationId: number
  /** 交付阶段：CD1_KICKOFF/CD2_DESIGN/CD3_BUILD/CD4_UAT/CD5_GO_LIVE */
  stage: string
  /** 交付物类型：STANDARD/SPECIFIC */
  type?: string
  /** 交付物名称 */
  name: string
  /** 交付物描述 */
  description?: string
  /** 交付物层级 */
  level?: string
  /** 责任人ID */
  ownerId?: number
}

/** 交付物状态变更 DTO */
export interface DeliveryItemStatusDTO {
  /** 交付物ID */
  id: number
  /** 目标状态：PENDING/SUBMITTED/ACCEPTED/REJECTED/WAIVED */
  targetStatus: string
  /** 变更原因（驳回/豁免时填写） */
  reason?: string
}
