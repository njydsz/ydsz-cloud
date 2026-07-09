/**
 * @file 用户管理 API
 * @description 提供用户的分页查询、详情、增删改、状态切换、密码重置、
 *              角色分配等接口；对应后端 UserController（/users）。
 *              涉及敏感操作（删除、重置密码）的接口需要在请求头携带 X-Re-Auth-Token。
 * @module api/system/user
 */

import { request } from '@/utils/request'
import type { PageData } from '@/types/api'
import type { UserVO, UserQuery, UserCreateDTO } from './types'

/**
 * 分页查询
 * @param query 分页及过滤条件
 * @returns 用户分页结果（后端返回 MyBatis-Plus Page，字段 records/total）
 */
export const listUsers = (query: UserQuery) =>
  request<PageData<UserVO>>({ url: '/users', method: 'GET', params: query })

/**
 * 详情
 * @param id 用户 ID
 * @returns 用户详情
 */
export const getUser = (id: number) =>
  request<UserVO>({ url: `/users/${id}`, method: 'GET' })

/**
 * 创建
 * @param data 用户创建表单数据
 * @returns 新建用户 ID
 */
export const createUser = (data: UserCreateDTO) =>
  request<number>({ url: '/users', method: 'POST', data })

/**
 * 更新
 * @param id   用户 ID
 * @param data 用户创建表单数据
 * @returns void
 */
export const updateUser = (id: number, data: UserCreateDTO) =>
  request<void>({ url: `/users/${id}`, method: 'PUT', data })

/**
 * 删除（需要 X-Re-Auth-Token）
 * @param id          用户 ID
 * @param reauthToken 二次验证 Token，缺失时不携带 X-Re-Auth-Token 头
 * @returns void
 */
export const deleteUser = (id: number, reauthToken?: string) =>
  request<void>({
    url: `/users/${id}`,
    method: 'DELETE',
    headers: reauthToken ? { 'X-Re-Auth-Token': reauthToken } : undefined,
  })

/**
 * 重置密码（需要 X-Re-Auth-Token）
 * @param id          用户 ID
 * @param newPassword 新密码
 * @param reauthToken 二次验证 Token，缺失时不携带 X-Re-Auth-Token 头
 * @returns void
 */
export const resetPassword = (id: number, newPassword: string, reauthToken?: string) =>
  request<void>({
    url: `/users/${id}/reset-password`,
    method: 'POST',
    data: { newPassword },
    headers: reauthToken ? { 'X-Re-Auth-Token': reauthToken } : undefined,
  })

/**
 * 启用/禁用用户
 * @param id     用户 ID
 * @param status 目标状态：ENABLED 启用 / DISABLED 禁用
 * @returns void
 */
export const toggleUserStatus = (id: number, status: string) =>
  request<void>({ url: `/users/${id}/status`, method: 'POST', params: { status } })

/**
 * 为用户分配角色
 * @param id      用户 ID
 * @param roleIds 角色 ID 列表（全量覆盖）
 * @returns void
 */
export const assignUserRoles = (id: number, roleIds: number[]) =>
  request<void>({ url: `/users/${id}/roles`, method: 'PUT', data: roleIds })

/**
 * 查询用户的角色 ID 列表
 * @param id 用户 ID
 * @returns 角色 ID 列表
 */
export const listUserRoles = (id: number) =>
  request<number[]>({ url: `/users/${id}/roles`, method: 'GET' })

export { request }
export type { UserVO, UserQuery, UserCreateDTO }
