/**
 * @file 资源分配 API 接口封装
 * @description 资源分配（ResourceAssignment）相关接口，对应后端 ResourceAssignmentController（/resource-assignments）。提供分配动作、按员工/项目查询、活跃项目数、利用率、分页查询等能力。
 * @module api/resource/assignment
 */
import { request } from '@/utils/request'
import type { ResourceAssignmentVO, ResourceAssignmentCreateDTO } from './types'

/**
 * 分配动作（单一入口，根据 action 执行 RESERVE/START/TRANSFER/RELEASE/CANCEL）
 * @param data 资源分配创建参数
 * @returns 新建分配记录 ID
 */
export const actResourceAssignment = (data: ResourceAssignmentCreateDTO) =>
  request<number>({ url: '/resource-assignments/act', method: 'POST', data })

/**
 * 查询分配详情
 * @param id 分配记录 ID
 * @returns 分配记录详情
 */
export const getResourceAssignment = (id: number) =>
  request<ResourceAssignmentVO>({ url: `/resource-assignments/${id}`, method: 'GET' })

/**
 * 按员工查询分配记录列表
 * @param employeeId 员工 ID
 * @returns 分配记录列表
 */
export const listAssignmentsByEmployee = (employeeId: number) =>
  request<ResourceAssignmentVO[]>({ url: `/resource-assignments/by-employee/${employeeId}`, method: 'GET' })

/**
 * 按立项查询分配记录列表
 * @param initiationId 立项 ID
 * @returns 分配记录列表
 */
export const listAssignmentsByInitiation = (initiationId: number) =>
  request<ResourceAssignmentVO[]>({ url: `/resource-assignments/by-initiation/${initiationId}`, method: 'GET' })

/**
 * 查询员工当前活跃项目数
 * @param employeeId 员工 ID
 * @returns 活跃项目数量
 */
export const activeCount = (employeeId: number) =>
  request<number>({ url: `/resource-assignments/active-count/${employeeId}`, method: 'GET' })

/**
 * 查询员工利用率
 * @param employeeId 员工 ID
 * @returns 利用率统计信息（键值对结构）
 */
export const utilization = (employeeId: number) =>
  request<Record<string, unknown>>({ url: `/resource-assignments/utilization/${employeeId}`, method: 'GET' })

/**
 * 分页查询分配记录
 * @param page 页码
 * @param size 每页条数
 * @param params 可选筛选条件（employeeId 员工 ID、initiationId 立项 ID、status 状态）
 * @returns 分页结果
 */
export const pageAssignments = (page: number, size: number, params?: { employeeId?: number; initiationId?: number; status?: string }) =>
  request<PageResult<ResourceAssignmentVO>>({
    url: '/resource-assignments/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })
