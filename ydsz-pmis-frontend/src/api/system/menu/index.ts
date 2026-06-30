import { request } from '@/utils/request'
import type { MenuTreeVO, PermissionFormDTO } from './types'

/**
 * 查询所有权限（树形）
 */
export const listPermissionTree = () =>
  request<MenuTreeVO[]>({ url: '/permissions/tree', method: 'GET' })

/**
 * 权限详情
 */
export const getPermission = (id: number) =>
  request<MenuTreeVO>({ url: `/permissions/${id}`, method: 'GET' })

/**
 * 创建权限
 */
export const createPermission = (data: PermissionFormDTO) =>
  request<number>({ url: '/permissions', method: 'POST', data })

/**
 * 更新权限
 */
export const updatePermission = (data: PermissionFormDTO) =>
  request<void>({ url: '/permissions', method: 'PUT', data })

/**
 * 删除权限
 */
export const deletePermission = (id: number) =>
  request<void>({ url: `/permissions/${id}`, method: 'DELETE' })
