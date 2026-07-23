/**
 * @file 异步导出 API 封装（下载中心）
 * @description 对接后端 AsyncExportController（/report/asyncExport），
 *              提供异步导出任务提交、记录查询、下载 URL 获取与记录删除能力。
 * @module api/report/export
 */
import { request } from '@/utils/request'

/** 导出记录状态（与后端 AsyncExportServiceImpl 对齐） */
export type ExportStatus = 'PENDING' | 'GENERATING' | 'COMPLETED' | 'FAILED'

/** 导出记录（映射后端 ydsz_export_record 表字段） */
export interface ExportRecord {
  /** 记录 ID */
  id: string
  /** 用户 ID */
  user_id: string
  /** 导出类型 */
  export_type: string
  /** 状态 */
  status: ExportStatus
  /** 文件 URL */
  file_url?: string
  /** 文件大小（字节） */
  file_size?: number
  /** 错误信息 */
  error_message?: string
  /** 查询参数（JSON） */
  params?: string
  /** 创建时间 */
  created_at: string
  /** 完成时间 */
  completed_at?: string
  /** 过期时间 */
  expired_at?: string
  /** 软删除标记 */
  deleted?: number
}

/** 提交导出任务参数 */
export interface SubmitExportParams {
  /** 导出类型 */
  exportType: string
  /** 查询参数 */
  params?: Record<string, unknown>
}

/** 提交导出任务响应 */
export interface SubmitExportResult {
  recordId: string
  status: string
}

/**
 * 提交异步导出任务
 * @param exportType 导出类型
 * @param params 查询参数
 * @returns 记录 ID 与初始状态
 */
export const submitExport = (exportType: string, params?: Record<string, unknown>) =>
  request<SubmitExportResult>({
    url: '/api/project/report/asyncExport/submit',
    method: 'POST',
    params: { exportType },
    data: params || {},
  })

/**
 * 查询导出记录列表（分页）
 * @param page 页码（从 1 开始）
 * @param size 每页条数
 * @returns 分页导出记录列表
 */
export const getExportRecords = (page = 1, size = 20) =>
  request<{
    content: ExportRecord[]
    totalElements: number
    totalPages: number
    number: number
  }>({
    url: '/api/project/report/asyncExport/records',
    method: 'GET',
    params: { page, size },
  })

/**
 * 获取下载 URL
 * @param recordId 记录 ID
 * @returns 下载 URL
 */
export const getDownloadUrl = (recordId: string) =>
  request<{ url: string; success: boolean }>({
    url: `/api/project/report/asyncExport/${recordId}/download`,
    method: 'GET',
  })

/**
 * 删除导出记录
 * @param recordId 记录 ID
 * @returns 操作结果
 */
export const deleteExportRecord = (recordId: string) =>
  request<{ success: boolean }>({
    url: `/api/project/report/asyncExport/${recordId}`,
    method: 'DELETE',
  })
