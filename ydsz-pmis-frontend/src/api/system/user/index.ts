import { request } from '@/utils/request'
import type { UserVO, UserQuery, UserCreateDTO } from './types'

/**
 * 分页查询
 */
export const listUsers = (query: UserQuery) =>
  request<PageResult<UserVO>>({ url: '/users', method: 'GET', params: query })

/**
 * 详情
 */
export const getUser = (id: number) =>
  request<UserVO>({ url: `/users/${id}`, method: 'GET' })

/**
 * 创建
 */
export const createUser = (data: UserCreateDTO) =>
  request<number>({ url: '/users', method: 'POST', data })

/**
 * 更新
 */
export const updateUser = (id: number, data: UserCreateDTO) =>
  request<void>({ url: `/users/${id}`, method: 'PUT', data })

/**
 * 删除
 */
export const deleteUser = (id: number) =>
  request<void>({ url: `/users/${id}`, method: 'DELETE' })

/**
 * 重置密码
 */
export const resetPassword = (id: number, newPassword: string) =>
  request<void>({ url: `/users/${id}/reset-password`, method: 'POST', params: { password: newPassword } })

/**
 * 启用/禁用用户
 */
export const toggleUserStatus = (id: number, status: string) =>
  request<void>({ url: `/users/${id}/status`, method: 'POST', params: { status } })

/**
 * 为用户分配角色
 */
export const assignUserRoles = (id: number, roleIds: number[]) =>
  request<void>({ url: `/users/${id}/roles`, method: 'PUT', data: roleIds })

/**
 * 查询用户的角色 ID 列表
 */
export const listUserRoles = (id: number) =>
  request<number[]>({ url: `/users/${id}/roles`, method: 'GET' })

export { request }
export type { UserVO, UserQuery, UserCreateDTO }
