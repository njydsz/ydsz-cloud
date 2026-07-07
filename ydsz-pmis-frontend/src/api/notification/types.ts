/**
 * @file 通知中心类型定义
 * @description 与后端 MsgNotificationDO / NotificationSendDTO / NotificationQueryDTO 对齐。
 *   - ID 类型为 String（后端 VARCHAR(20) 雪花算法字符串）
 *   - readStatus：0=未读，1=已读（后端 Integer）
 *   - level：INFO/WARN/ERROR/URGENT
 *   - category：SYSTEM/WORKFLOW/ALERT/TODO/ANNOUNCE
 *   - recallStatus：NONE/RECALLED
 * @module api/notification/types
 */

/** 通知 VO（对齐后端 MsgNotificationDO） */
export interface NotificationVO {
  /** 主键 ID（雪花算法字符串） */
  id: string
  /** 通知标题 */
  title: string
  /** 通知内容(支持富文本/Markdown) */
  content: string
  /** 通知级别：INFO/WARN/ERROR/URGENT */
  level?: string
  /** 通知分类：SYSTEM/WORKFLOW/ALERT/TODO/ANNOUNCE */
  category?: string
  /** 发送优先级: LOW/NORMAL/HIGH/URGENT */
  priority?: string
  /** 发送人 ID（系统通知为 SYSTEM） */
  senderId?: string
  /** 接收人 ID */
  receiverId: string
  /** 关联业务类型 */
  bizType?: string
  /** 关联业务单据 ID */
  bizId?: string
  /** 聚合组 */
  messageGroup?: string
  /** 聚合批次 ID */
  batchId?: string
  /** 点击跳转 URL */
  actionUrl?: string
  /** 跳转按钮文案 */
  actionText?: string
  /** 通知图标标识 */
  icon?: string
  /** 扩展字段 JSON */
  extra?: string
  /** 来源模块 */
  sourceModule?: string
  /** 已读状态：0=未读，1=已读 */
  readStatus: number
  /** 首次阅读时间 */
  readTime?: string
  /** 撤回状态：NONE/RECALLED */
  recallStatus?: string
  /** 撤回时间 */
  recallAt?: string
  /** 过期时间 */
  expiredAt?: string
  /** 创建人 */
  createdBy?: string
  /** 创建时间 */
  createdAt?: string
  /** 更新人 */
  updatedBy?: string
  /** 更新时间 */
  updatedAt?: string
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
  /** 发送优先级 */
  priority?: string
  /** 发送人 ID */
  senderId?: string
  /** 接收人 ID（单发） */
  receiverId?: string
  /** 接收人 ID 列表（群发，与 receiverId 二选一） */
  receiverIds?: string[]
  /** 业务类型 */
  bizType?: string
  /** 业务对象 ID */
  bizId?: string
  /** 聚合组 */
  messageGroup?: string
  /** 点击跳转 URL */
  actionUrl?: string
  /** 跳转按钮文案 */
  actionText?: string
  /** 通知图标 */
  icon?: string
  /** 扩展字段 JSON */
  extra?: string
  /** 来源模块 */
  sourceModule?: string
  /** 过期时间 */
  expiredAt?: string
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
