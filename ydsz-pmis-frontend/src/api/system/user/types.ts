import type { PageQuery, BaseVO } from '@/types/api'

export interface UserVO extends BaseVO {
  username: string
  realName: string
  email?: string
  phone?: string
  levelCode?: string
  levelName?: string
  departmentId?: number
  departmentName?: string
  positionId?: number
  positionName?: string
}

export interface UserQuery extends PageQuery {
  departmentId?: number
  levelCode?: string
  status?: string
}

export interface UserCreateDTO {
  username: string
  realName: string
  password?: string
  email?: string
  phone?: string
  levelCode?: string
  departmentId?: number
  positionId?: number
  roleIds?: number[]
  status?: string
}
