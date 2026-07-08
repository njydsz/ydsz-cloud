/**
 * @file 消息通知引擎(message) 类型定义
 * @description 与后端 message 模块的 VO/DTO/Query 对齐。
 *   - 所有 ID 类型为 string（后端 VARCHAR(20) 雪花算法字符串）
 *   - status 发送状态：PENDING/SENDING/SUCCESS/FAILED/RETRY/DEAD/RECALLED
 *   - channel 通道：SMS/EMAIL/PUSH/IN_APP/WEBHOOK/DINGTALK/WECOM/FEISHU
 *   - priority 优先级：LOW/NORMAL/HIGH/URGENT
 *   - recallStatus 撤回状态：NONE/RECALLED
 *   - receiptStatus 回执状态：NONE/DELIVERED/READ/CLICKED/FAILED
 *   - template status：ENABLED/DISABLED
 *   - template auditStatus：DRAFT/AUDITING/APPROVED/REJECTED
 * @module api/message/types
 */

/** 消息发送状态枚举 */
export type MessageStatus =
  | 'PENDING'
  | 'SENDING'
  | 'SUCCESS'
  | 'FAILED'
  | 'RETRY'
  | 'DEAD'
  | 'RECALLED'

/** 消息通道枚举 */
export type MessageChannel =
  | 'SMS'
  | 'EMAIL'
  | 'PUSH'
  | 'IN_APP'
  | 'WEBHOOK'
  | 'DINGTALK'
  | 'WECOM'
  | 'FEISHU'

/** 发送优先级枚举 */
export type MessagePriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'

/** 撤回状态枚举 */
export type RecallStatus = 'NONE' | 'RECALLED'

/** 回执状态枚举 */
export type ReceiptStatus = 'NONE' | 'DELIVERED' | 'READ' | 'CLICKED' | 'FAILED'

/** 启用/禁用状态枚举 */
export type EnableStatus = 'ENABLED' | 'DISABLED'

/** 模板审核状态枚举 */
export type TemplateAuditStatus = 'DRAFT' | 'AUDITING' | 'APPROVED' | 'REJECTED'

/** 消息日志展示对象（对齐后端 MsgLogDO） */
export interface MessageLogVO {
  /** 主键 ID（雪花算法字符串） */
  id: string
  /** 发送通道 */
  channel: MessageChannel
  /** 业务类型 */
  bizType?: string
  /** 业务单据 ID */
  bizId?: string
  /** 接收人（API 响应自动脱敏） */
  receiver?: string
  /** 模板编码 */
  templateCode?: string
  /** 模板参数 JSON */
  templateParams?: string
  /** 发送内容(渲染后) */
  content?: string
  /** 发送状态 */
  status: MessageStatus
  /** 错误信息 */
  errorMessage?: string
  /** 发送优先级 */
  priority?: MessagePriority
  /** 触发发送的用户 ID */
  senderId?: string
  /** 聚合组 */
  messageGroup?: string
  /** 聚合批次 ID */
  batchId?: string
  /** 命中的路由规则 ID */
  routeRuleId?: string
  /** 是否灰度命中: 0 正式 / 1 灰度 */
  canary?: number
  /** 灰度实验键 */
  canaryKey?: string
  /** 幂等去重键 */
  dedupKey?: string
  /** 撤回状态 */
  recallStatus?: RecallStatus
  /** 撤回时间 */
  recallAt?: string
  /** 回执状态 */
  receiptStatus?: ReceiptStatus
  /** 回执到达时间 */
  receiptAt?: string
  /** 已重试次数 */
  retryCount?: number
  /** 下次重试时间 */
  nextRetryAt?: string
  /** 三方服务商回执 ID */
  providerTraceId?: string
  /** 发送耗时(毫秒) */
  costMs?: number
  /** 系统链路追踪 ID */
  traceId?: string
  /** RocketMQ 消息 ID */
  msgId?: string
  /** RocketMQ Topic */
  topic?: string
  /** RocketMQ 重试次数 */
  reconsumeTimes?: number
  /** 租户 ID */
  tenantId?: string
  /** 父消息 ID */
  parentMsgId?: string
  /** 创建时间 */
  createdAt?: string
  /** 更新时间 */
  updatedAt?: string
}

/** 消息日志分页查询参数（对齐后端 MessageLogQueryDTO） */
export interface MessageLogPageQuery {
  /** 页码 */
  page: number
  /** 每页条数 */
  size: number
  /** 通道 */
  channel?: MessageChannel
  /** 业务类型 */
  bizType?: string
  /** 业务单据 ID */
  bizId?: string
  /** 发送状态 */
  status?: MessageStatus
  /** 接收人 */
  receiver?: string
  /** 模板编码 */
  templateCode?: string
  /** 发送优先级 */
  priority?: MessagePriority
  /** 撤回状态 */
  recallStatus?: RecallStatus
  /** 统计起始时间（yyyy-MM-dd） */
  startDate?: string
  /** 统计结束时间（yyyy-MM-dd） */
  endDate?: string
  /** 租户 ID */
  tenantId?: string
}

/** 批次进度展示对象 */
export interface BatchProgressVO {
  /** 批次 ID */
  batchId: string
  /** 总数 */
  total: number
  /** 成功数 */
  success: number
  /** 失败数 */
  failed: number
  /** 进行中数 */
  pending: number
  /** 进度(%) */
  progress?: number
}

/** 消息模板展示对象（对齐后端 MsgTemplateDO） */
export interface MsgTemplateVO {
  /** 主键 ID */
  id: string
  /** 模板编码 */
  templateCode: string
  /** 通道 */
  channel: MessageChannel
  /** 语言区域 */
  locale?: string
  /** 语义版本 */
  version?: string
  /** 模板分类 */
  category?: string
  /** 场景编码 */
  sceneCode?: string
  /** 主题(EMAIL 专用) */
  subject?: string
  /** 模板内容 */
  content?: string
  /** 供应商 */
  provider?: string
  /** 供应商侧模板 ID */
  providerKey?: string
  /** 短信签名 */
  signName?: string
  /** 状态: ENABLED/DISABLED */
  status: EnableStatus
  /** 审核状态: DRAFT/AUDITING/APPROVED/REJECTED */
  auditStatus?: TemplateAuditStatus
  /** 审核人 ID */
  auditBy?: string
  /** 审核时间 */
  auditAt?: string
  /** 审核备注 */
  auditRemark?: string
  /** 描述说明 */
  description?: string
  /** 租户 ID */
  tenantId?: string
  /** 创建时间 */
  createdAt?: string
  /** 更新时间 */
  updatedAt?: string
}

/** 模板创建/更新 DTO（对齐后端 TemplateCreateDTO） */
export interface TemplateCreateDTO {
  /** 主键 ID（更新时必传） */
  id?: string
  /** 模板编码 */
  templateCode: string
  /** 通道 */
  channel: MessageChannel
  /** 语言区域 */
  locale?: string
  /** 语义版本 */
  version?: string
  /** 模板分类 */
  category?: string
  /** 场景编码 */
  sceneCode?: string
  /** 主题(EMAIL 专用) */
  subject?: string
  /** 模板内容 */
  content?: string
  /** 供应商 */
  provider?: string
  /** 供应商侧模板 ID */
  providerKey?: string
  /** 短信签名 */
  signName?: string
  /** 描述说明 */
  description?: string
}

/** 模板分页查询参数（对齐后端 TemplateQueryDTO） */
export interface TemplateQueryDTO {
  /** 页码 */
  page: number
  /** 每页条数 */
  size: number
  /** 模板编码 */
  templateCode?: string
  /** 通道 */
  channel?: MessageChannel
  /** 语言区域 */
  locale?: string
  /** 状态: ENABLED/DISABLED */
  status?: EnableStatus
  /** 审核状态 */
  auditStatus?: TemplateAuditStatus
  /** 模板分类 */
  category?: string
  /** 场景编码 */
  sceneCode?: string
}

/** 模板审核 DTO（对齐后端 TemplateAuditDTO） */
export interface TemplateAuditDTO {
  /** 模板 ID */
  id: string
  /** 审核状态: DRAFT/AUDITING/APPROVED/REJECTED */
  auditStatus: TemplateAuditStatus
  /** 审核备注 */
  auditRemark?: string
}

/** 路由规则展示对象（对齐后端 MsgRouteRuleDO） */
export interface MsgRouteRuleVO {
  /** 主键 ID */
  id: string
  /** 规则编码(租户内唯一) */
  ruleCode: string
  /** 规则名称 */
  ruleName?: string
  /** 业务类型 */
  bizType?: string
  /** 通道 */
  channel?: MessageChannel
  /** 优先级(数值越小越优先) */
  priority?: number
  /** 路由条件(SpEL 表达式) */
  conditionExpr?: string
  /** 命中后目标通道 */
  targetChannel?: MessageChannel
  /** 目标通道发送失败时降级通道 */
  fallbackChannel?: MessageChannel
  /** 多级降级链(逗号分隔通道列表) */
  fallbackChain?: string
  /** 状态: ENABLED/DISABLED */
  status: EnableStatus
  /** 描述说明 */
  description?: string
  /** 排序序号 */
  sortOrder?: number
  /** 租户 ID */
  tenantId?: string
  /** 创建时间 */
  createdAt?: string
  /** 更新时间 */
  updatedAt?: string
}

/** 路由规则新增/更新 DTO（对齐后端 RouteRuleUpsertDTO） */
export interface RouteRuleUpsertDTO {
  /** 主键 ID（更新时必传） */
  id?: string
  /** 规则编码 */
  ruleCode: string
  /** 规则名称 */
  ruleName?: string
  /** 业务类型 */
  bizType?: string
  /** 通道 */
  channel?: MessageChannel
  /** 优先级(数值越小越优先) */
  priority?: number
  /** 路由条件(SpEL 表达式) */
  conditionExpr?: string
  /** 命中后目标通道 */
  targetChannel?: MessageChannel
  /** 目标通道发送失败时降级通道 */
  fallbackChannel?: MessageChannel
  /** 状态: ENABLED/DISABLED */
  status: EnableStatus
  /** 描述说明 */
  description?: string
  /** 排序序号 */
  sortOrder?: number
}

/** 灰度桶展示对象（对齐后端 MsgCanaryDO） */
export interface MsgCanaryVO {
  /** 主键 ID */
  id: string
  /** 灰度键(如 template_code 或 biz_type) */
  canaryKey: string
  /** 桶总数(默认 100) */
  bucketTotal?: number
  /** 命中的桶列表 JSON */
  bucketSelected?: string
  /** 灰度比例(0-100) */
  percentage: number
  /** 灰度命中后切换的实验模板编码 */
  experimentTemplateCode?: string
  /** 灰度命中后切换的实验通道 */
  experimentChannel?: MessageChannel
  /** 状态: ENABLED/DISABLED */
  status: EnableStatus
  /** 描述说明 */
  description?: string
  /** 租户 ID */
  tenantId?: string
  /** 创建时间 */
  createdAt?: string
  /** 更新时间 */
  updatedAt?: string
}

/** 灰度桶新增/更新 DTO（对齐后端 CanaryUpsertDTO） */
export interface CanaryUpsertDTO {
  /** 主键 ID（更新时必传） */
  id?: string
  /** 灰度键 */
  canaryKey: string
  /** 桶总数(默认 100) */
  bucketTotal?: number
  /** 灰度比例(0-100) */
  percentage: number
  /** 灰度命中后切换的实验模板编码 */
  experimentTemplateCode?: string
  /** 灰度命中后切换的实验通道 */
  experimentChannel?: MessageChannel
  /** 状态: ENABLED/DISABLED */
  status: EnableStatus
  /** 描述说明 */
  description?: string
}

/** 灰度命中检查参数 */
export interface CanaryHitQuery {
  /** 灰度键 */
  canaryKey: string
  /** 桶值 */
  bucketValue: string
}

/** A/B 分组统计（对照组/实验组共用） */
export interface CanaryGroupStats {
  /** 总发送量 */
  total: number
  /** 发送成功数 */
  success: number
  /** 发送失败数 */
  failed: number
  /** 重试中数 */
  retry: number
  /** 死信数 */
  dead: number
  /** 已送达数 */
  delivered: number
  /** 已读数 */
  read: number
  /** 已点击数 */
  clicked: number
  /** 成功率(%) */
  successRate: number
  /** 送达率(%) */
  deliveryRate: number
  /** 阅读率(%) */
  readRate: number
}

/** 灰度 A/B 实验报表（对齐后端 CanaryReportVO） */
export interface CanaryReportVO {
  /** 灰度键 */
  canaryKey: string
  /** 对照组(未命中灰度)统计 */
  control: CanaryGroupStats
  /** 实验组(命中灰度)统计 */
  treatment: CanaryGroupStats
  /** 统计起始时间 */
  start?: string
  /** 统计结束时间 */
  end?: string
}

/** 灰度 A/B 报表查询参数 */
export interface CanaryReportQuery {
  /** 灰度键 */
  canaryKey: string
  /** 起始时间 */
  start: string
  /** 结束时间 */
  end: string
}

/** 消息发送总览统计（对齐后端 MessageStatsVO） */
export interface MessageStatsVO {
  /** 总发送量 */
  total: number
  /** 发送成功数 */
  success: number
  /** 发送失败数 */
  failed: number
  /** 重试中数 */
  retry: number
  /** 死信数 */
  dead: number
  /** 已撤回数 */
  recalled: number
  /** 成功率(%) */
  successRate: number
  /** 死信率(%) */
  deadRate: number
  /** 统计起始时间 */
  start?: string
  /** 统计结束时间 */
  end?: string
}

/** 按通道维度的发送统计（对齐后端 ChannelStatsVO） */
export interface ChannelStatsVO {
  /** 通道 */
  channel: MessageChannel
  /** 总发送量 */
  total: number
  /** 发送成功数 */
  success: number
  /** 发送失败数 */
  failed: number
  /** 重试中数 */
  retry: number
  /** 死信数 */
  dead: number
  /** 成功率(%) */
  successRate: number
  /** 死信率(%) */
  deadRate: number
}

/** 回执统计（对齐后端 ReceiptStatsVO） */
export interface ReceiptStatsVO {
  /** 成功发送总数（回执分母） */
  total: number
  /** 已送达 */
  delivered: number
  /** 已读 */
  read: number
  /** 已点击 */
  clicked: number
  /** 投递失败 */
  failed: number
  /** 回执超时 */
  timeout: number
  /** 无回执 */
  none: number
  /** 送达率(%) */
  deliveryRate: number
  /** 已读率(%) */
  readRate: number
}

/** 统计查询参数（起止日期） */
export interface StatsQuery {
  /** 起始时间（yyyy-MM-dd） */
  start: string
  /** 结束时间（yyyy-MM-dd） */
  end: string
}
