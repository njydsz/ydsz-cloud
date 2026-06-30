import type { PageQuery } from '@/types/api'

export interface ConfigVO {
  id: number
  configGroup: string
  configKey: string
  configValue: string
  defaultValue?: string
  /** STRING/NUMBER/BOOLEAN/JSON */
  valueType: string
  description?: string
  /** 1=前端可见, 0=私有 */
  isPublic: number
  sortOrder?: number
  status: string
  createdBy?: number
  createdAt?: string
  updatedBy?: number
  updatedAt?: string
}

export interface ConfigQuery extends PageQuery {
  configGroup?: string
  status?: string
  isPublic?: number
}

export interface ConfigFormDTO {
  id?: number
  configGroup: string
  configKey: string
  configValue?: string
  defaultValue?: string
  valueType?: string
  description?: string
  isPublic?: number
  sortOrder?: number
  status?: string
}
