/**
 * @file 菜单与权限管理 API
 * @description 提供菜单/权限的树形查询、详情、增删改等接口；
 *              对应后端 PermissionController（/permissions）。
 * @module api/system/menu
 */

import { request } from '@/utils/request'
import type { MenuTreeVO, PermissionFormDTO } from './types'

/**
 * 查询所有权限（树形）
 * @returns 权限树形结构列表
 */
export const listPermissionTree = () =>
  request<MenuTreeVO[]>({ url: '/permissions/tree', method: 'GET' })

/**
 * 权限详情
 * @param id 权限 ID
 * @returns 权限详情
 */
export const getPermission = (id: number) =>
  request<MenuTreeVO>({ url: `/permissions/${id}`, method: 'GET' })

/**
 * 创建权限
 * @param data 权限表单数据
 * @returns 新建权限 ID
 */
export const createPermission = (data: PermissionFormDTO) =>
  request<number>({ url: '/permissions', method: 'POST', data })

/**
 * 更新权限
 * @param data 权限表单数据（必须含 id）
 * @returns void
 */
export const updatePermission = (data: PermissionFormDTO) =>
  request<void>({ url: '/permissions', method: 'PUT', data })

/**
 * 删除权限
 * @param id 权限 ID
 * @returns void
 */
export const deletePermission = (id: number) =>
  request<void>({ url: `/permissions/${id}`, method: 'DELETE' })
