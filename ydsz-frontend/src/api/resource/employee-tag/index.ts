/**
 * @file 员工标签 API 接口封装
 * @description 员工标签（EmployeeTag）相关接口，对应后端 EmployeeTagController（/employee-tags）。提供标签新增、删除、覆盖式设置、按员工查询、按标签筛选候选人等能力。
 * @module api/resource/employee-tag
 */
import { request } from '@/utils/request'
import type { EmployeeTagVO, EmployeeTagCreateDTO } from './types'

/**
 * 添加员工标签
 * @param data 员工标签创建参数
 * @returns 新建标签 ID
 */
export const addEmployeeTag = (data: EmployeeTagCreateDTO) =>
  request<number>({ url: '/employee-tags', method: 'POST', data })

/**
 * 删除员工标签
 * @param id 标签 ID
 * @returns 无返回值
 */
export const removeEmployeeTag = (id: number) =>
  request<void>({ url: `/employee-tags/${id}`, method: 'DELETE' })

/**
 * 覆盖式设置员工标签（先清空再批量写入）
 * @param employeeId 员工 ID
 * @param tags 标签列表
 * @returns 无返回值
 */
export const replaceEmployeeTags = (employeeId: number, tags: EmployeeTagCreateDTO[]) =>
  request<void>({ url: `/employee-tags/replace/${employeeId}`, method: 'PUT', data: tags })

/**
 * 按员工查询标签列表
 * @param employeeId 员工 ID
 * @returns 员工标签列表
 */
export const listEmployeeTags = (employeeId: number) =>
  request<EmployeeTagVO[]>({ url: `/employee-tags/by-employee/${employeeId}`, method: 'GET' })

/**
 * 按标签类型/编码筛选候选人
 * @param tagType 标签类型（SKILL/TECH/INDUSTRY/AVAILABILITY）
 * @param tagCode 标签编码（可选）
 * @returns 候选人标签列表
 */
export const findCandidates = (tagType: string, tagCode?: string) =>
  request<EmployeeTagVO[]>({ url: '/employee-tags/candidates', method: 'GET', params: { tagType, tagCode } })
