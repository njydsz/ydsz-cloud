import type { BaseVO } from '@/types/api'

/**
 * 权限/菜单节点
 */
export interface MenuTreeVO extends BaseVO {
  id: number
  parentId: number
  permCode: string
  permName: string
  permType: 'MENU' | 'BUTTON' | 'API'
  path?: string
  component?: string
  icon?: string
  sortOrder?: number
  visible?: number
  children?: MenuTreeVO[]
}

/**
 * 权限表单 DTO
 */
export interface PermissionFormDTO {
  id?: number
  parentId?: number
  permCode: string
  permName: string
  permType: 'MENU' | 'BUTTON' | 'API'
  path?: string
  component?: string
  icon?: string
  sortOrder?: number
  visible?: number
  status?: string
}
