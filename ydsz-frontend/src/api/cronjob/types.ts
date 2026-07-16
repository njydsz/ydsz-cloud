/**
 * @file 分布式任务引擎(cronjob) 类型定义
 * @description 与后端 cronjob 模块的 VO/DTO/Query 对齐。
 *   - 所有 ID 类型为 string（后端 VARCHAR(20) 雪花算法字符串）
 *   - status 任务状态：NORMAL/PAUSED/ERROR/AUTO_PAUSED/COMPLETE
 *   - scheduleType 调度类型：CRON/FIXED_RATE/FIXED_DELAY/API
 *   - jobType 任务类型：BEAN/GLUE/MAPREDUCE
 *   - 日志状态：RUNNING/SUCCESS/FAILED/TIMEOUT/ZOMBIE
 * @module api/cronjob/types
 */

/** 任务状态枚举 */
export type JobStatus = 'NORMAL' | 'PAUSED' | 'ERROR' | 'AUTO_PAUSED' | 'COMPLETE'

/** 调度类型枚举 */
export type ScheduleType = 'CRON' | 'FIXED_RATE' | 'FIXED_DELAY' | 'API'

/** 任务类型枚举 */
export type JobType = 'BEAN' | 'GLUE' | 'MAPREDUCE'

/** 日志执行状态枚举 */
export type LogStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'TIMEOUT' | 'ZOMBIE'

/** 阻塞策略枚举 */
export type BlockStrategy = 'SERIAL_EXECUTION' | 'DISCARD_LATER' | 'COVER_EARLY'

/** Misfire 策略枚举 */
export type MisfirePolicy = 'FIRE_ONCE' | 'IGNORE' | 'FIRE_AND_PROCEED'

/** 退避策略枚举 */
export type RetryBackoff = 'FIXED' | 'LINEAR' | 'EXPONENTIAL'

/** 触发类型枚举 */
export type TriggerType = 'CRON' | 'MANUAL' | 'API' | 'RETRY' | 'PARENT'

/** 日志级别枚举 */
export type LogLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR'

/** SLA 告警级别 */
export type AlertLevel = 'INFO' | 'WARN' | 'CRITICAL'

/** GLUE 语言 */
export type GlueLanguage = 'JAVA' | 'PYTHON' | 'SHELL' | 'GROOVY' | 'JS'

/** 任务展示对象（对齐后端 JobVO） */
export interface JobVO {
  /** 主键 ID（雪花算法字符串） */
  id: string
  /** 任务名称 */
  jobName: string
  /** 任务分组 */
  jobGroup?: string
  /** 任务 KEY（唯一） */
  jobKey: string
  /** 处理器（Bean 名/类全名） */
  handler?: string
  /** Cron 表达式（scheduleType=CRON 时使用） */
  cronExpression?: string
  /** 调度类型：CRON/FIXED_RATE/FIXED_DELAY/API */
  scheduleType: ScheduleType
  /** 固定频率(毫秒)（scheduleType=FIXED_RATE 时使用） */
  fixedRateMs?: number
  /** 固定延迟(毫秒)（scheduleType=FIXED_DELAY 时使用） */
  fixedDelayMs?: number
  /** 任务类型：BEAN/GLUE/MAPREDUCE */
  jobType: JobType
  /** 任务状态：NORMAL/PAUSED/ERROR/AUTO_PAUSED/COMPLETE */
  status: JobStatus
  /** 参数 JSON */
  paramsJson?: string
  /** 备注 */
  remark?: string
  /** 下次触发时间 */
  nextFireTime?: string
  /** 上次触发时间 */
  lastFireTime?: string
  /** 累计触发次数 */
  fireCount?: number
  /** 成功次数 */
  successCount?: number
  /** 失败次数 */
  failCount?: number
  /** 分片总数 */
  shardTotal?: number
  /** Misfire 策略 */
  misfirePolicy?: MisfirePolicy
  /** 阻塞策略 */
  blockStrategy?: BlockStrategy
  /** 最大重试次数 */
  maxRetries?: number
  /** 重试间隔(毫秒) */
  retryIntervalMs?: number
  /** 退避策略 */
  retryBackoff?: RetryBackoff
  /** 最大连续失败次数（超过自动暂停） */
  maxConsecutiveFails?: number
  /** 自动恢复时间(分钟)（自动暂停后 N 分钟自动恢复） */
  autoResumeAfterMinutes?: number
  /** 优先级 */
  priority?: number
  /** 版本号 */
  version?: number
  /** 时区 */
  timezone?: string
  /** 分布式锁 TTL(毫秒) */
  lockTtlMs?: number
  /** 执行超时(毫秒) */
  timeoutMs?: number
  /** 慢任务阈值(毫秒) */
  slowThresholdMs?: number
  /** 租户 ID */
  tenantId?: string
  /** 创建时间 */
  createdAt?: string
  /** 更新时间 */
  updatedAt?: string
}

/** 任务创建/更新 DTO（对齐后端 JobSaveDTO） */
export interface JobSaveDTO {
  /** 主键 ID（更新时必传） */
  id?: string
  /** 任务名称 */
  jobName: string
  /** 任务分组 */
  jobGroup?: string
  /** 任务 KEY（唯一） */
  jobKey: string
  /** 处理器 */
  handler?: string
  /** Cron 表达式 */
  cronExpression?: string
  /** 调度类型 */
  scheduleType: ScheduleType
  /** 固定频率(毫秒) */
  fixedRateMs?: number
  /** 固定延迟(毫秒) */
  fixedDelayMs?: number
  /** 任务类型 */
  jobType: JobType
  /** 参数 JSON */
  paramsJson?: string
  /** 备注 */
  remark?: string
  /** 分片总数 */
  shardTotal?: number
  /** Misfire 策略 */
  misfirePolicy?: MisfirePolicy
  /** 阻塞策略 */
  blockStrategy?: BlockStrategy
  /** 最大重试次数 */
  maxRetries?: number
  /** 重试间隔(毫秒) */
  retryIntervalMs?: number
  /** 退避策略 */
  retryBackoff?: RetryBackoff
  /** 最大连续失败次数 */
  maxConsecutiveFails?: number
  /** 自动恢复时间(分钟) */
  autoResumeAfterMinutes?: number
  /** 优先级 */
  priority?: number
  /** 时区 */
  timezone?: string
  /** 分布式锁 TTL(毫秒) */
  lockTtlMs?: number
  /** 执行超时(毫秒) */
  timeoutMs?: number
  /** 慢任务阈值(毫秒) */
  slowThresholdMs?: number
}

/** 任务分页查询参数 */
export interface JobPageQuery {
  /** 页码 */
  page: number
  /** 每页条数 */
  size: number
  /** 任务 KEY（模糊匹配） */
  jobKey?: string
  /** 任务状态 */
  status?: JobStatus
  /** 任务分组 */
  jobGroup?: string
  /** 租户 ID */
  tenantId?: string
}

/** 执行日志展示对象（对齐后端 JobLogVO） */
export interface JobLogVO {
  /** 主键 ID */
  id: string
  /** 任务 ID */
  jobId: string
  /** 任务 KEY */
  jobKey: string
  /** 开始时间 */
  startTime?: string
  /** 结束时间 */
  endTime?: string
  /** 执行耗时(毫秒) */
  durationMs?: number
  /** 执行状态：RUNNING/SUCCESS/FAILED/TIMEOUT/ZOMBIE */
  status: LogStatus
  /** 错误信息 */
  errorMessage?: string
  /** 参数 JSON */
  paramsJson?: string
  /** 结果 JSON */
  resultJson?: string
  /** 链路追踪 ID */
  traceId?: string
  /** 触发类型：CRON/MANUAL/API/RETRY/PARENT */
  triggerType?: TriggerType
  /** 执行节点 ID */
  execNodeId?: string
  /** 分片索引 */
  shardIndex?: number
  /** 分片总数 */
  shardTotal?: number
  /** 创建时间 */
  createdAt?: string
}

/** 执行日志分页查询参数 */
export interface JobLogPageQuery {
  /** 页码 */
  page: number
  /** 每页条数 */
  size: number
  /** 任务 ID */
  jobId?: string
  /** 执行状态 */
  status?: LogStatus
  /** 开始时间 */
  startTime?: string
  /** 结束时间 */
  endTime?: string
}

/** 日志内容展示对象（对齐后端 JobLogContentVO） */
export interface JobLogContentVO {
  /** 主键 ID */
  id: string
  /** 日志 ID */
  logId: string
  /** 任务 KEY */
  jobKey?: string
  /** 行号 */
  lineNo: number
  /** 日志级别：DEBUG/INFO/WARN/ERROR */
  logLevel: LogLevel
  /** 日志内容 */
  content: string
  /** 创建时间 */
  createdAt?: string
}

/** 日志内容分页查询参数 */
export interface JobLogContentPageQuery {
  /** 页码 */
  page: number
  /** 每页条数 */
  size: number
  /** 日志 ID */
  logId: string
}

/** 子任务展示对象（MapReduce，对齐后端 JobTaskVO） */
export interface JobTaskVO {
  /** 主键 ID */
  id: string
  /** 任务 ID */
  jobId: string
  /** 日志 ID */
  logId: string
  /** 任务 KEY */
  jobKey?: string
  /** 子任务名称 */
  taskName: string
  /** 子任务参数 */
  taskParams?: string
  /** 子任务类型 */
  taskType?: string
  /** 执行状态 */
  status?: LogStatus
  /** 执行结果 */
  result?: string
  /** 错误信息 */
  errorMessage?: string
  /** 执行节点 ID */
  execNodeId?: string
  /** 创建时间 */
  createdAt?: string
}

/** 子任务分页查询参数 */
export interface JobTaskPageQuery {
  /** 页码 */
  page: number
  /** 每页条数 */
  size: number
  /** 日志 ID */
  logId: string
}

/** 每日统计展示对象（对齐后端 JobDailyStatsVO） */
export interface JobDailyStatsVO {
  /** 主键 ID */
  id?: string
  /** 任务 ID */
  jobId: string
  /** 任务 KEY */
  jobKey?: string
  /** 统计日期（yyyy-MM-dd） */
  statsDate: string
  /** 触发次数 */
  fireCount: number
  /** 成功次数 */
  successCount: number
  /** 失败次数 */
  failCount: number
  /** 超时次数 */
  timeoutCount: number
  /** 平均耗时(毫秒) */
  avgDurationMs?: number
  /** 最大耗时(毫秒) */
  maxDurationMs?: number
  /** 最小耗时(毫秒) */
  minDurationMs?: number
  /** P95 耗时(毫秒) */
  p95DurationMs?: number
}

/** 每日统计查询参数 */
export interface JobDailyStatsQuery {
  /** 任务 ID */
  jobId: string
  /** 起始日期（yyyy-MM-dd） */
  startDate: string
  /** 结束日期（yyyy-MM-dd） */
  endDate: string
}

/** 汇总统计展示对象 */
export interface JobSummaryStatsVO {
  /** 任务 ID */
  jobId: string
  /** 总触发次数 */
  totalFire: number
  /** 总成功次数 */
  totalSuccess: number
  /** 总失败次数 */
  totalFail: number
  /** 总超时次数 */
  totalTimeout: number
  /** 平均耗时(毫秒) */
  avgDurationMs?: number
  /** P95 耗时(毫秒) */
  p95DurationMs?: number
  /** 成功率（0-1） */
  successRate?: number
}

/** SLA 展示对象（对齐后端 JobSlaVO） */
export interface JobSlaVO {
  /** 主键 ID */
  id: string
  /** 任务 ID */
  jobId: string
  /** 任务 KEY */
  jobKey?: string
  /** 最大执行耗时(毫秒) */
  maxDurationMs?: number
  /** 最大失败率（0-1） */
  maxFailRate?: number
  /** 最低成功率（0-1） */
  minSuccessRate?: number
  /** 告警级别：INFO/WARN/CRITICAL */
  alertLevel?: AlertLevel
  /** 是否启用 */
  enabled?: boolean
  /** 创建时间 */
  createdAt?: string
  /** 更新时间 */
  updatedAt?: string
}

/** SLA 创建/更新 DTO */
export interface JobSlaSaveDTO {
  /** 主键 ID（更新时必传） */
  id?: string
  /** 任务 ID */
  jobId: string
  /** 最大执行耗时(毫秒) */
  maxDurationMs?: number
  /** 最大失败率（0-1） */
  maxFailRate?: number
  /** 最低成功率（0-1） */
  minSuccessRate?: number
  /** 告警级别 */
  alertLevel?: AlertLevel
  /** 是否启用 */
  enabled?: boolean
}

/** SLA 分页查询参数 */
export interface JobSlaPageQuery {
  /** 页码 */
  page: number
  /** 每页条数 */
  size: number
}

/** 历史版本展示对象（对齐后端 JobHistoryVO） */
export interface JobHistoryVO {
  /** 主键 ID */
  id: string
  /** 任务 ID */
  jobId: string
  /** 版本号 */
  version: number
  /** 配置快照 JSON */
  snapshot?: string
  /** 任务名称（快照） */
  jobName?: string
  /** 任务 KEY（快照） */
  jobKey?: string
  /** 处理器（快照） */
  handler?: string
  /** Cron 表达式（快照） */
  cronExpression?: string
  /** 修改人 */
  changedBy?: string
  /** 修改时间 */
  changedAt?: string
}

/** GLUE 代码展示对象（对齐后端 GlueCodeVO） */
export interface GlueCodeVO {
  /** 主键 ID */
  id: string
  /** 任务 ID */
  jobId: string
  /** 源代码 */
  sourceCode: string
  /** 语言：JAVA/PYTHON/SHELL/GROOVY/JS */
  language: GlueLanguage
  /** 版本号 */
  version: number
  /** 备注 */
  remark?: string
  /** 创建人 */
  createdBy?: string
  /** 创建时间 */
  createdAt?: string
}

/** GLUE 代码保存 DTO */
export interface GlueCodeSaveDTO {
  /** 任务 ID */
  jobId: string
  /** 源代码 */
  sourceCode: string
  /** 语言 */
  language: GlueLanguage
  /** 备注 */
  remark?: string
}

/** 批量操作请求体 */
export interface JobBatchRequest {
  /** 任务 ID 列表 */
  jobIds: string[]
}
