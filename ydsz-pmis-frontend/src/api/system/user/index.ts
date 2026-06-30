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
  request<void>({ url: `/users/${id}/reset-password`, method: 'POST', params: { newPassword } })

export { request }
export type { UserVO, UserQuery, UserCreateDTO }
