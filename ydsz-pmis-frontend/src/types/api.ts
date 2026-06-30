/**
 * 通用 API 类型
 */
export interface PageQuery {
  page?: number
  size?: number
  sort?: string
  order?: 'asc' | 'desc'
  keyword?: string
}

export interface PageResult<T = unknown> {
  records: T[]
  total: number
  page: number
  size: number
  pages?: number
}

export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  traceId?: string
}

export interface BaseVO {
  id: number
  createdBy?: number
  createdAt?: string
  updatedBy?: number
  updatedAt?: string
  status?: string
}

export interface OptionVO<T = string | number> {
  label: string
  value: T
  disabled?: boolean
  children?: OptionVO<T>[]
}

export interface TreeNode<T = unknown> {
  id: number
  parentId: number
  children?: TreeNode<T>[]
  [key: string]: unknown
}
