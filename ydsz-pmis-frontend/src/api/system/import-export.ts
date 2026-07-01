/**
 * @file 统一数据导入导出 API
 * @description 统一数据导入导出 API（批次 19 P3-3 落地）。
 *              提供模板下载、文件上传导入、导入历史查询等接口；
 *              对应后端 ImportExportController（/api/v1/execution/import）。
 * @module api/system/import-export
 */

import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

/** 业务类型：rate-card 费率卡 / rate-internal 内部费率 / time-entry 工时 */
export type BizType = 'rate-card' | 'rate-internal' | 'time-entry'

/** 单条错误 */
export interface ImportError {
  /** 行号（从 1 开始） */
  rowIndex: number
  /** 出错字段名 */
  field: string
  /** 错误信息 */
  message: string
  /** 出错字段的原始值 */
  value: string
}

/** 导入结果 */
export interface ImportResult {
  /** 是否全部成功 */
  success: boolean
  /** 成功条数 */
  successCount: number
  /** 失败条数 */
  failCount: number
  /** 错误明细列表 */
  errors: ImportError[]
}

/**
 * 下载空白模板（带样例数据）
 * @param bizType 业务类型
 * @returns 模板文件二进制 Blob
 */
export function downloadTemplate(bizType: BizType): Promise<Blob> {
  return request.get(`/api/v1/execution/import/template/${bizType}`, {
    responseType: 'blob'
  }) as unknown as Promise<Blob>
}

/**
 * 上传文件执行导入
 * @param bizType 业务类型
 * @param file    待导入的 Excel/CSV 文件
 * @returns 统一响应包装的导入结果
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
 * @param params 分页参数：page 页码 / size 每页条数
 * @returns 统一响应包装的导入历史列表
 */
export function getImportHistory(params: { page?: number; size?: number }): Promise<ApiResponse<unknown>> {
  return request.get('/api/v1/execution/import/history', { params }) as unknown as Promise<ApiResponse<unknown>>
}
