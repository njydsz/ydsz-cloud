import type { BaseVO, PageQuery } from '@/types/api'

export interface DictTypeVO extends BaseVO {
  typeCode: string
  typeName: string
  description?: string
  itemCount?: number
}

export interface DictItemVO extends BaseVO {
  typeCode: string
  itemCode: string
  itemValue: string
  sortOrder?: number
  status: string
}

export interface DictTypeFormDTO {
  typeCode: string
  typeName: string
  description?: string
}

export interface DictItemFormDTO {
  id?: number
  typeCode: string
  itemCode: string
  itemValue: string
  sortOrder?: number
  status?: string
}

export interface DictItemQuery extends PageQuery {
  typeCode: string
  keyword?: string
}
