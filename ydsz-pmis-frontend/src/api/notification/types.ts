/**
 * @file 通知中心类型定义
 * @description 与后端 NotificationDO / NotificationSendDTO / NotificationQueryDTO 对齐。
 *   - readStatus：0=未读，1=已读（后端 Integer）
 *   - level：INFO/WARN/ERROR/URGENT
 *   - category：SYSTEM/WORKFLOW/ALERT/TODO
 * @module api/notification/types
 */

/** 通知 VO（对齐后端 NotificationDO） */
export interface NotificationVO {
  /** 主键 ID */
  id: number
  /** 标题 */
  title: string
  /** 内容 */
  content: string
  /** 通知级别：INFO/WARN/ERROR/URGENT */
  level?: string
  /** 通知分类：SYSTEM/WORKFLOW/ALERT/TODO */
  category?: string
  /** 发送人 ID */
  senderId?: number
  /** 接收人 ID */
  receiverId: number
  /** 业务类型 */
  bizType?: string
  /** 业务对象 ID */
  bizId?: string
  /** 已读状态：0=未读，1=已读 */
  readStatus: number
  /** 已读时间 */
  readTime?: string
  /** 过期时间 */
  expiredAt?: string
  /** 创建人 */
  createdBy?: number
  /** 创建时间 */
  createdAt?: string
}

/** 通知发送 DTO（对齐后端 NotificationSendDTO） */
export interface NotificationSendDTO {
  /** 标题 */
  title: string
  /** 内容 */
  content: string
  /** 通知级别 */
  level?: string
  /** 通知分类 */
  category?: string
  /** 发送人 ID */
  senderId?: number
  /** 接收人 ID（单发） */
  receiverId?: number
  /** 接收人 ID 列表（群发，与 receiverId 二选一） */
  receiverIds?: number[]
  /** 业务类型 */
  bizType?: string
  /** 业务对象 ID */
  bizId?: string
  /** 过期时间 */
  expiredAt?: string
  /** 是否同时发送邮件 */
  emailEnabled?: boolean
  /** 收件人邮箱（emailEnabled=true 时必填） */
  receiverEmail?: string
}

/** 通知分页查询参数（对齐后端 NotificationQueryDTO） */
export interface NotificationPageQuery {
  /** 页码 */
  page: number
  /** 每页条数 */
  size: number
  /** 通知分类 */
  category?: string
  /** 通知级别 */
  level?: string
  /** 已读状态：0=未读，1=已读 */
  readStatus?: number
}
