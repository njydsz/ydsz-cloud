import type { BaseVO, PageQuery } from '@/types/api'

export interface RoleVO extends BaseVO {
  roleCode: string
  roleName: string
  description?: string
  dataScope: 'ALL' | 'DEPT' | 'SELF' | 'CUSTOM' | string
  sortOrder?: number
  status: string
  permissionIds?: number[]
}

export interface RoleQuery extends PageQuery {
  keyword?: string
  status?: string
}

export interface RoleFormDTO {
  id?: number
  roleCode: string
  roleName: string
  description?: string
  dataScope: string
  sortOrder?: number
  status?: string
  permissionIds?: number[]
}
