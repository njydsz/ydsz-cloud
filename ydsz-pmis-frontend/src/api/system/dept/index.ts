import { request } from '@/utils/request'
import type { DeptVO, DeptFormDTO } from './types'

/**
 * 查询部门树
 */
export const listDeptTree = () =>
  request<DeptVO[]>({ url: '/departments/tree', method: 'GET' })

/**
 * 所有部门(扁平)
 */
export const listDepts = () =>
  request<DeptVO[]>({ url: '/departments', method: 'GET' })

/**
 * 详情
 */
export const getDept = (id: number) =>
  request<DeptVO>({ url: `/departments/${id}`, method: 'GET' })

/**
 * 创建
 */
export const createDept = (data: DeptFormDTO) =>
  request<number>({ url: '/departments', method: 'POST', data })

/**
 * 更新
 */
export const updateDept = (data: DeptFormDTO) =>
  request<void>({ url: '/departments', method: 'PUT', data })

/**
 * 删除
 */
export const deleteDept = (id: number) =>
  request<void>({ url: `/departments/${id}`, method: 'DELETE' })
