/**
 * @file 员工 API 接口封装
 * @description 员工管理相关接口，对应后端 EmployeeController（/employees）。
 * @module api/resource/employee
 */
import { request } from '@/utils/request'
import type { EmployeeVO, EmployeeCreateDTO, EmployeeUpdateDTO } from './types'

/** 分页查询员工 */
export const pageEmployees = (params: {
  page?: number
  size?: number
  keyword?: string
  departmentId?: string
  employeeType?: string
  workStatus?: string
}) => request<{ records: EmployeeVO[]; total: number; current: number; size: number }>({
  url: '/employees',
  method: 'GET',
  params,
})

/** 查询员工详情 */
export const getEmployee = (id: string) =>
  request<EmployeeVO>({ url: `/employees/${id}`, method: 'GET' })

/** 创建员工 */
export const createEmployee = (data: EmployeeCreateDTO) =>
  request<string>({ url: '/employees', method: 'POST', data })

/** 更新员工 */
export const updateEmployee = (id: string, data: EmployeeUpdateDTO) =>
  request<void>({ url: `/employees/${id}`, method: 'PUT', data })

/** 删除员工 */
export const deleteEmployee = (id: string) =>
  request<void>({ url: `/employees/${id}`, method: 'DELETE' })

/** 按部门查询员工列表 */
export const listEmployeesByDepartment = (departmentId: string) =>
  request<EmployeeVO[]>({ url: `/employees/by-department/${departmentId}`, method: 'GET' })
