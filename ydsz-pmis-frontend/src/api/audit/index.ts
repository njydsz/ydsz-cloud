/**
 * 审计中心 API
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'

export interface OperationLogVO {
  id?: number
  module?: string
  action?: string
  bizType?: string
  bizId?: string
  userId?: number
  username?: string
  requestUrl?: string
  httpMethod?: string
  methodSignature?: string
  clientIp?: string
  userAgent?: string
  paramsJson?: string
  responseJson?: string
  /** SUCCESS / FAILED */
  status?: string
  errorMessage?: string
  costMs?: number
  traceId?: string
  createdAt?: string
}

export interface LoginAuditVO {
  id?: number
  username?: string
  userId?: number
  loginAt?: string
  loginIp?: string
  userAgent?: string
  /** SUCCESS / FAIL_PASSWORD / FAIL_LOCKED ... */
  status?: string
  failReason?: string
  mfaUsed?: boolean
  mfaSuccess?: boolean
  traceId?: string
}

export interface DataExportAuditVO {
  id?: number
  userId?: number
  username?: string
  exportModule?: string
  exportAction?: string
  bizType?: string
  rowCount?: number
  fileName?: string
  fileSize?: number
  exportFormat?: string
  querySummary?: string
  clientIp?: string
  exportedAt?: string
  traceId?: string
}

export interface SensitiveOperationVO {
  id?: number
  userId?: number
  username?: string
  opType?: string
  opTarget?: string
  targetId?: string
  opResult?: string
  reAuthUsed?: boolean
  clientIp?: string
  operatedAt?: string
  traceId?: string
}

/** 操作日志分页 */
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

/** 登录审计分页 */
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

/** 敏感操作分页 */
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

/** 数据导出分页 */
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

/** 清理 N 天前日志 */
export const cleanOperationLog = (days: number) =>
  request<number>({
    url: '/audit/operation/clean',
    method: 'POST',
    params: { days },
  })
