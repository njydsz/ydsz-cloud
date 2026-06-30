import { request } from '@/utils/request'
import type { RoleVO, RoleQuery, RoleFormDTO } from './types'

/**
 * 角色分页
 */
export const listRoles = (query: RoleQuery) =>
  request<PageResult<RoleVO>>({ url: '/roles', method: 'GET', params: query })

/**
 * 全部启用的角色 (下拉用)
 */
export const listAllRoles = () =>
  request<RoleVO[]>({ url: '/roles/all', method: 'GET' })

/**
 * 详情
 */
export const getRole = (id: number) =>
  request<RoleVO>({ url: `/roles/${id}`, method: 'GET' })

/**
 * 创建
 */
export const createRole = (data: RoleFormDTO) =>
  request<number>({ url: '/roles', method: 'POST', data })

/**
 * 更新
 */
export const updateRole = (data: RoleFormDTO) =>
  request<void>({ url: '/roles', method: 'PUT', data })

/**
 * 删除
 */
export const deleteRole = (id: number) =>
  request<void>({ url: `/roles/${id}`, method: 'DELETE' })

/**
 * 为角色分配权限
 */
export const assignPermissions = (roleId: number, permissionIds: number[]) =>
  request<void>({ url: `/roles/${roleId}/permissions`, method: 'PUT', data: permissionIds })

/**
 * 查询角色的权限 ID 列表
 */
export const listRolePermissions = (roleId: number) =>
  request<number[]>({ url: `/roles/${roleId}/permissions`, method: 'GET' })
