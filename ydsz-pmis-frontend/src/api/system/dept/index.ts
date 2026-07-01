/**
 * @file 部门管理 API
 * @description 提供部门的树形查询、扁平列表、详情、增删改等接口；
 *              对应后端 DepartmentController（/departments）。
 * @module api/system/dept
 */

import { request } from '@/utils/request'
import type { DeptVO, DeptFormDTO } from './types'

/**
 * 查询部门树
 * @returns 部门树形结构列表
 */
export const listDeptTree = () =>
  request<DeptVO[]>({ url: '/departments/tree', method: 'GET' })

/**
 * 所有部门(扁平)
 * @returns 全量部门扁平列表
 */
export const listDepts = () =>
  request<DeptVO[]>({ url: '/departments', method: 'GET' })

/**
 * 详情
 * @param id 部门 ID
 * @returns 部门详情
 */
export const getDept = (id: number) =>
  request<DeptVO>({ url: `/departments/${id}`, method: 'GET' })

/**
 * 创建
 * @param data 部门表单数据
 * @returns 新建部门 ID
 */
export const createDept = (data: DeptFormDTO) =>
  request<number>({ url: '/departments', method: 'POST', data })

/**
 * 更新
 * @param data 部门表单数据（必须含 id）
 * @returns void
 */
export const updateDept = (data: DeptFormDTO) =>
  request<void>({ url: '/departments', method: 'PUT', data })

/**
 * 删除
 * @param id 部门 ID
 * @returns void
 */
export const deleteDept = (id: number) =>
  request<void>({ url: `/departments/${id}`, method: 'DELETE' })
