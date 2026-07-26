/**
 * @file 员工 API 接口封装
 * @description 员工管理相关接口，对应后端 EmployeeController（/employees）。
 * @module api/resource/employee
 */
import { request } from '@/utils/request'
import type { EmployeeVO, EmployeeCreateDTO, EmployeeUpdateDTO } from './types'

/**
 * 分页查询员工
 * @param params 查询参数（页码、每页条数、关键字、部门ID、员工类型、工作状态）
 * @returns 员工分页结果
 */
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

/**
 * 查询员工详情
 * @param id 员工 ID
 * @returns 员工详情
 */
export const getEmployee = (id: string) =>
  request<EmployeeVO>({ url: `/employees/${id}`, method: 'GET' })

/**
 * 创建员工
 * @param data 员工创建参数
 * @returns 新建员工 ID
 */
export const createEmployee = (data: EmployeeCreateDTO) =>
  request<string>({ url: '/employees', method: 'POST', data })

/**
 * 更新员工
 * @param id 员工 ID
 * @param data 员工更新参数
 * @returns 无返回值
 */
export const updateEmployee = (id: string, data: EmployeeUpdateDTO) =>
  request<void>({ url: `/employees/${id}`, method: 'PUT', data })

/**
 * 删除员工
 * @param id 员工 ID
 * @returns 无返回值
 */
export const deleteEmployee = (id: string) =>
  request<void>({ url: `/employees/${id}`, method: 'DELETE' })

/**
 * 按部门查询员工列表
 * @param departmentId 部门 ID
 * @returns 员工列表
 */
export const listEmployeesByDepartment = (departmentId: string) =>
  request<EmployeeVO[]>({ url: `/employees/by-department/${departmentId}`, method: 'GET' })
