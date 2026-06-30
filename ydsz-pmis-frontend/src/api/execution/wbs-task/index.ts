import { request } from '@/utils/request'
import type { WbsTaskVO, WbsTaskCreateDTO, WbsTaskStatusDTO } from './types'

export const pageWbsTasks = (
  page: number,
  size: number,
  params?: { keyword?: string; status?: string; initiationId?: number; ownerId?: number },
) =>
  request<PageResult<WbsTaskVO>>({
    url: '/execution/wbs-task/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

export const getWbsTask = (id: number) =>
  request<WbsTaskVO>({ url: `/execution/wbs-task/${id}`, method: 'GET' })

export const createWbsTask = (data: WbsTaskCreateDTO) =>
  request<number>({ url: '/execution/wbs-task', method: 'POST', data })

export const updateWbsTask = (data: Partial<WbsTaskVO> & { id: number }) =>
  request<void>({ url: '/execution/wbs-task', method: 'PUT', data })

export const changeWbsTaskStatus = (data: WbsTaskStatusDTO) =>
  request<void>({ url: '/execution/wbs-task/status', method: 'PUT', data })

export const deleteWbsTask = (id: number) =>
  request<void>({ url: `/execution/wbs-task/${id}`, method: 'DELETE' })
