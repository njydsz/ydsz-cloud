import { request } from '@/utils/request'
import type { ProjectClosureVO, ProjectClosureCreateDTO, ProjectClosureStatusDTO } from './types'

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

export const getProjectClosure = (id: number) =>
  request<ProjectClosureVO>({
    url: `/execution/project-closure/${id}`,
    method: 'GET',
  })

export const createProjectClosure = (data: ProjectClosureCreateDTO) =>
  request<number>({
    url: '/execution/project-closure',
    method: 'POST',
    data,
  })

export const changeProjectClosureStatus = (data: ProjectClosureStatusDTO) =>
  request<void>({
    url: '/execution/project-closure/status',
    method: 'PUT',
    data,
  })
