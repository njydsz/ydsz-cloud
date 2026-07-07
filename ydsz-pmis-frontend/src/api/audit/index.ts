/**
 * @file 审计中心 API
 * @description 提供操作日志、登录审计、敏感操作、数据导出审计的分页查询与日志清理能力，
 *              对应后端 AuditController（/audit/**）。
 * @module api/audit
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'

/**
 * 操作日志视图对象
 */
export interface OperationLogVO {
  /** 日志 ID */
  id?: number
  /** 业务模块 */
  module?: string
  /** 操作动作 */
  action?: string
  /** 业务类型 */
  bizType?: string
  /** 业务 ID */
  bizId?: string
  /** 操作用户 ID */
  userId?: number
  /** 操作用户名 */
  username?: string
  /** 请求 URL */
  requestUrl?: string
  /** HTTP 方法 */
  httpMethod?: string
  /** 后端方法签名 */
  methodSignature?: string
  /** 客户端 IP */
  clientIp?: string
  /** User-Agent */
  userAgent?: string
  /** 请求参数 JSON */
  paramsJson?: string
  /** 响应结果 JSON */
  responseJson?: string
  /** SUCCESS / FAILED */
  status?: string
  /** 失败时的错误信息 */
  errorMessage?: string
  /** 耗时（毫秒） */
  costMs?: number
  /** 链路追踪 ID */
  traceId?: string
  /** 创建时间（ISO 8601） */
  createdAt?: string
}

/**
 * 操作日志字段级变更对比（Diff）
 */
export interface FieldDiffVO {
  /** 字段名 */
  field?: string
  /** 原值 */
  oldValue?: string
  /** 新值 */
  newValue?: string
  /** 变更类型：ADD 新增 / DELETE 删除 / MODIFY 修改 */
  changeType?: 'ADD' | 'DELETE' | 'MODIFY' | string
}

/**
 * 登录审计视图对象
 */
export interface LoginAuditVO {
  /** 日志 ID */
  id?: number
  /** 用户名 */
  username?: string
  /** 用户 ID */
  userId?: number
  /** 登录时间（ISO 8601） */
  loginAt?: string
  /** 登录 IP */
  loginIp?: string
  /** User-Agent */
  userAgent?: string
  /** SUCCESS / FAIL_PASSWORD / FAIL_LOCKED ... */
  status?: string
  /** 失败原因 */
  failReason?: string
  /** 是否使用 2FA */
  mfaUsed?: boolean
  /** 2FA 是否校验通过 */
  mfaSuccess?: boolean
  /** 链路追踪 ID */
  traceId?: string
}

/**
 * 数据导出审计视图对象
 */
export interface DataExportAuditVO {
  /** 日志 ID */
  id?: number
  /** 操作用户 ID */
  userId?: number
  /** 操作用户名 */
  username?: string
  /** 导出模块 */
  exportModule?: string
  /** 导出动作 */
  exportAction?: string
  /** 业务类型 */
  bizType?: string
  /** 导出行数 */
  rowCount?: number
  /** 文件名 */
  fileName?: string
  /** 文件大小（字节） */
  fileSize?: number
  /** 导出格式（XLSX / CSV 等） */
  exportFormat?: string
  /** 查询条件摘要 */
  querySummary?: string
  /** 客户端 IP */
  clientIp?: string
  /** 导出时间（ISO 8601） */
  exportedAt?: string
  /** 链路追踪 ID */
  traceId?: string
}

/**
 * 敏感操作审计视图对象
 */
export interface SensitiveOperationVO {
  /** 日志 ID */
  id?: number
  /** 操作用户 ID */
  userId?: number
  /** 操作用户名 */
  username?: string
  /** 操作类型 */
  opType?: string
  /** 操作目标 */
  opTarget?: string
  /** 目标 ID */
  targetId?: string
  /** 操作结果 */
  opResult?: string
  /** 是否使用了二次认证 */
  reAuthUsed?: boolean
  /** 客户端 IP */
  clientIp?: string
  /** 操作时间（ISO 8601） */
  operatedAt?: string
  /** 链路追踪 ID */
  traceId?: string
}

/**
 * 操作日志分页
 *
 * 按用户、业务类型、状态、模块筛选操作日志。
 *
 * @param page 页码
 * @param size 每页大小
 * @param params 可选筛选条件（userId / bizType / status / module）
 * @returns 操作日志分页结果
 */
export const pageOperationLog = (
  page: number,
  size: number,
  params?: { userId?: number; bizType?: string; status?: string; module?: string },
) =>
  request<PageResult<OperationLogVO>>({
    url: '/audit/operation/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/**
 * 按业务实体查询操作日志列表
 *
 * 根据业务类型与业务 ID 查询其关联的操作日志（变更历史），
 * 用于 EntityHistoryDrawer 等组件展示实体变更轨迹。
 * 对应后端 OperationLogController#byBiz（GET /audit/operation/by-biz）。
 *
 * @param bizType 业务类型
 * @param bizId   业务单据 ID
 * @param limit   最大条数（默认 50，最大 100）
 * @returns 操作日志列表
 */
export const getOperationLogByBiz = (
  bizType: string,
  bizId: string | number,
  limit: number = 50,
) =>
  request<OperationLogVO[]>({
    url: '/audit/operation/by-biz',
    method: 'GET',
    params: { bizType, bizId, limit },
  })

/**
 * 查询指定操作日志的字段级变更对比（Diff）
 *
 * 对应后端 OperationLogController#getDiff（GET /audit/operation/{id}/diff）。
 *
 * @param logId 操作日志 ID
 * @returns 字段级变更对比列表
 */
export const getOperationLogDiff = (logId: number) =>
  request<FieldDiffVO[]>({
    url: `/audit/operation/${logId}/diff`,
    method: 'GET',
  })

/**
 * 登录审计分页
 *
 * 按用户名、状态、IP 筛选登录审计记录。
 *
 * @param page 页码
 * @param size 每页大小
 * @param params 可选筛选条件（username / status / loginIp）
 * @returns 登录审计分页结果
 */
export const pageLoginAudit = (
  page: number,
  size: number,
  params?: { username?: string; status?: string; loginIp?: string },
) =>
  request<PageResult<LoginAuditVO>>({
    url: '/audit/login/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/**
 * 敏感操作分页
 *
 * 按用户、操作类型筛选敏感操作审计记录。
 *
 * @param page 页码
 * @param size 每页大小
 * @param params 可选筛选条件（userId / opType）
 * @returns 敏感操作分页结果
 */
export const pageSensitiveOp = (
  page: number,
  size: number,
  params?: { userId?: number; opType?: string },
) =>
  request<PageResult<SensitiveOperationVO>>({
    url: '/audit/sensitive-op/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/**
 * 数据导出分页
 *
 * 按用户、导出模块、导出动作筛选数据导出审计记录。
 *
 * @param page 页码
 * @param size 每页大小
 * @param params 可选筛选条件（userId / exportModule / exportAction）
 * @returns 数据导出分页结果
 */
export const pageDataExport = (
  page: number,
  size: number,
  params?: { userId?: number; exportModule?: string; exportAction?: string },
) =>
  request<PageResult<DataExportAuditVO>>({
    url: '/audit/export/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/**
 * 清理 N 天前日志
 *
 * 物理删除指定天数之前的操作日志，释放存储空间。
 *
 * @param days 保留天数，删除此天数之前的日志
 * @returns 实际删除的记录数
 */
export const cleanOperationLog = (days: number) =>
  request<number>({
    url: '/audit/operation/clean',
    method: 'POST',
    params: { days },
  })
