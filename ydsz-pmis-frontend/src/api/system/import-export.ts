/**
 * 统一数据导入导出 API（批次 19 P3-3 落地）
 */

import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

/** 业务类型 */
export type BizType = 'rate-card' | 'rate-internal' | 'time-entry'

/** 单条错误 */
export interface ImportError {
  rowIndex: number
  field: string
  message: string
  value: string
}

/** 导入结果 */
export interface ImportResult {
  success: boolean
  successCount: number
  failCount: number
  errors: ImportError[]
}

/**
 * 下载空白模板（带样例数据）
 */
export function downloadTemplate(bizType: BizType): Promise<Blob> {
  return request.get(`/api/v1/execution/import/template/${bizType}`, {
    responseType: 'blob'
  }) as unknown as Promise<Blob>
}

/**
 * 上传文件执行导入
 */
export function importData(bizType: BizType, file: File): Promise<ApiResponse<ImportResult>> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/api/v1/execution/import/${bizType}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }) as unknown as Promise<ApiResponse<ImportResult>>
}

/**
 * 查询导入历史
 */
export function getImportHistory(params: { page?: number; size?: number }): Promise<ApiResponse<unknown>> {
  return request.get('/api/v1/execution/import/history', { params }) as unknown as Promise<ApiResponse<unknown>>
}
