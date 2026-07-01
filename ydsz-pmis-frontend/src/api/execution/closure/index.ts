/**
 * @file 项目结项 API 接口封装
 * @description 提供项目结项的分页查询、详情查询、创建结项申请、变更结项状态等能力，
 *              对应后端 ProjectClosureController（/execution/project-closure）。
 * @module api/execution/closure
 */
import { request } from '@/utils/request'
import type { ProjectClosureVO, ProjectClosureCreateDTO, ProjectClosureStatusDTO } from './types'

/**
 * 分页查询项目结项申请
 * @param page 页码
 * @param size 每页大小
 * @param params 额外筛选条件（关键字、状态、类型、立项 ID，可选）
 * @returns 结项申请分页结果
 */
export const pageProjectClosures = (
  page: number,
  size: number,
  params?: { keyword?: string; status?: string; type?: string; initiationId?: number },
) =>
  request<PageResult<ProjectClosureVO>>({
    url: '/execution/project-closure/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/**
 * 查询项目结项申请详情
 * @param id 结项申请 ID
 * @returns 结项申请详情对象
 */
export const getProjectClosure = (id: number) =>
  request<ProjectClosureVO>({
    url: `/execution/project-closure/${id}`,
    method: 'GET',
  })

/**
 * 创建项目结项申请
 * @param data 结项申请参数（结项编号、立项 ID、类型、原因等）
 * @returns 新建结项申请 ID
 */
export const createProjectClosure = (data: ProjectClosureCreateDTO) =>
  request<number>({
    url: '/execution/project-closure',
    method: 'POST',
    data,
  })

/**
 * 变更项目结项申请状态
 * @param data 状态变更参数（结项 ID、目标状态、原因）
 * @returns 无返回值
 */
export const changeProjectClosureStatus = (data: ProjectClosureStatusDTO) =>
  request<void>({
    url: '/execution/project-closure/status',
    method: 'PUT',
    data,
  })
