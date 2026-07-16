/**
 * @file 角色管理 API
 * @description 提供角色的分页查询、全量列表、详情、增删改、权限分配等接口；
 *              对应后端 RoleController（/roles）。
 * @module api/system/role
 */

import { request } from '@/utils/request'
import type { RoleVO, RoleQuery, RoleFormDTO } from './types'

/**
 * 角色分页
 * @param query 分页及过滤条件
 * @returns 角色分页结果
 */
export const listRoles = (query: RoleQuery) =>
  request<PageResult<RoleVO>>({ url: '/roles', method: 'GET', params: query })

/**
 * 全部启用的角色 (下拉用)
 * @returns 全部启用状态角色列表
 */
export const listAllRoles = () =>
  request<RoleVO[]>({ url: '/roles/all', method: 'GET' })

/**
 * 详情
 * @param id 角色 ID
 * @returns 角色详情
 */
export const getRole = (id: number) =>
  request<RoleVO>({ url: `/roles/${id}`, method: 'GET' })

/**
 * 创建
 * @param data 角色表单数据
 * @returns 新建角色 ID
 */
export const createRole = (data: RoleFormDTO) =>
  request<number>({ url: '/roles', method: 'POST', data })

/**
 * 更新
 * @param data 角色表单数据（必须含 id）
 * @returns void
 */
export const updateRole = (data: RoleFormDTO) =>
  request<void>({ url: '/roles', method: 'PUT', data })

/**
 * 删除
 * @param id 角色 ID
 * @returns void
 */
export const deleteRole = (id: number) =>
  request<void>({ url: `/roles/${id}`, method: 'DELETE' })

/**
 * 为角色分配权限
 * @param roleId        角色 ID
 * @param permissionIds 权限 ID 列表（全量覆盖）
 * @returns void
 */
export const assignPermissions = (roleId: number, permissionIds: number[]) =>
  request<void>({ url: `/roles/${roleId}/permissions`, method: 'PUT', data: permissionIds })

/**
 * 查询角色的权限 ID 列表
 * @param roleId 角色 ID
 * @returns 权限 ID 列表
 */
export const listRolePermissions = (roleId: number) =>
  request<number[]>({ url: `/roles/${roleId}/permissions`, method: 'GET' })
