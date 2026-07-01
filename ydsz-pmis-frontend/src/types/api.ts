/**
 * PMIS 通用 API 类型（批次 19 P2-4 落地）
 *
 * 收口 79 个 any 警告的核心：
 *   1. ApiResponse<T> 统一后端 R<T> 包装
 *   2. PageData<T> 统一分页响应
 *   3. PageQuery 统一分页请求
 *   4. BusinessEntity 业务实体基类
 *   5. 配合 .eslintrc.cjs 中 no-explicit-any: error 强制收口
 */

/** 分页查询参数（与后端 PageQuery 一致） */
export interface PageQuery {
  page?: number
  size?: number
  sort?: string
  order?: 'asc' | 'desc'
  keyword?: string
  [key: string]: unknown
}

/** 分页响应（与后端 PageResult 一致） */
export interface PageData<T> {
  records: T[]
  total: number
  page: number
  size: number
  pages?: number
}

/** 统一 R 包装响应（与后端 com.njydsz.pmis.common.api.R<T> 一致） */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  traceId?: string
  timestamp?: number
}

/** 业务实体基类 */
export interface BusinessEntity {
  id: number
  createdBy?: number
  createdAt?: string
  updatedBy?: number
  updatedAt?: string
  status?: string
}

/** 下拉项 / 树节点 */
export interface OptionVO<T = string | number> {
  label: string
  value: T
  disabled?: boolean
  children?: OptionVO<T>[]
}

export interface TreeNode<T> {
  id: number
  parentId: number
  children?: TreeNode<T>[]
}

/** 工具类型：提取 ApiResponse.data */
export type ApiData<T> = T extends ApiResponse<infer U> ? U : never

/** 工具类型：从 ApiResponse<T> 提取非空 data（如果 data 可能为 null/undefined） */
export type SafeApiData<T> = Exclude<ApiData<T>, null | undefined>

/** 工具类型：分页 + 数据合并 */
export type PagedApiResponse<T> = ApiResponse<PageData<T>>

/** 请求来源标识（与后端 X-Request-Source 对应） */
export const PMIS_REQUEST_SOURCE = 'PMIS-FRONTEND'

/** HTTP 业务错误码（与后端 ResultCode 对应） */
export enum ResultCode {
  SUCCESS = 0,
  UNAUTHORIZED = 401,
  FORBIDDEN = 403,
  NOT_FOUND = 404,
  SERVER_ERROR = 500,
  BIZ_ERROR = 1000,
  VALIDATION_ERROR = 1001,
  IDEMPOTENT_CONFLICT = 2001
}
