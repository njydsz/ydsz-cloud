import { request } from '@/utils/request'
import type { ResourceAssignmentVO, ResourceAssignmentCreateDTO } from './types'

/** 分配动作 (RESERVE/START/TRANSFER/RELEASE/CANCEL) */
export const actResourceAssignment = (data: ResourceAssignmentCreateDTO) =>
  request<number>({ url: '/resource-assignments/act', method: 'POST', data })

/** 详情 */
export const getResourceAssignment = (id: number) =>
  request<ResourceAssignmentVO>({ url: `/resource-assignments/${id}`, method: 'GET' })

/** 按员工查询 */
export const listAssignmentsByEmployee = (employeeId: number) =>
  request<ResourceAssignmentVO[]>({ url: `/resource-assignments/by-employee/${employeeId}`, method: 'GET' })

/** 按项目查询 */
export const listAssignmentsByInitiation = (initiationId: number) =>
  request<ResourceAssignmentVO[]>({ url: `/resource-assignments/by-initiation/${initiationId}`, method: 'GET' })

/** 员工活跃项目数 */
export const activeCount = (employeeId: number) =>
  request<number>({ url: `/resource-assignments/active-count/${employeeId}`, method: 'GET' })

/** 员工利用率 */
export const utilization = (employeeId: number) =>
  request<Record<string, unknown>>({ url: `/resource-assignments/utilization/${employeeId}`, method: 'GET' })

/** 分页查询 */
export const pageAssignments = (page: number, size: number, params?: { employeeId?: number; initiationId?: number; status?: string }) =>
  request<PageResult<ResourceAssignmentVO>>({
    url: '/resource-assignments/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })
