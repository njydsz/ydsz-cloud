import { request } from '@/utils/request'
import type { EmployeeTagVO, EmployeeTagCreateDTO } from './types'

/** 添加标签 */
export const addEmployeeTag = (data: EmployeeTagCreateDTO) =>
  request<number>({ url: '/employee-tags', method: 'POST', data })

/** 删除标签 */
export const removeEmployeeTag = (id: number) =>
  request<void>({ url: `/employee-tags/${id}`, method: 'DELETE' })

/** 覆盖式设置员工标签 */
export const replaceEmployeeTags = (employeeId: number, tags: EmployeeTagCreateDTO[]) =>
  request<void>({ url: `/employee-tags/replace/${employeeId}`, method: 'PUT', data: tags })

/** 按员工查询 */
export const listEmployeeTags = (employeeId: number) =>
  request<EmployeeTagVO[]>({ url: `/employee-tags/by-employee/${employeeId}`, method: 'GET' })

/** 按标签筛选候选人 */
export const findCandidates = (tagType: string, tagCode?: string) =>
  request<EmployeeTagVO[]>({ url: '/employee-tags/candidates', method: 'GET', params: { tagType, tagCode } })
