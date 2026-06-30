import { request } from '@/utils/request'
import type { TimeEntryVO, TimeEntryCreateDTO, TimeEntryApprovalDTO } from './types'

export const pageTimeEntries = (
  page: number,
  size: number,
  params?: {
    keyword?: string
    status?: string
    employeeId?: number
    initiationId?: number
    startDate?: string
    endDate?: string
  },
) =>
  request<PageResult<TimeEntryVO>>({
    url: '/execution/time-entry/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

export const getTimeEntry = (id: number) =>
  request<TimeEntryVO>({ url: `/execution/time-entry/${id}`, method: 'GET' })

export const createTimeEntry = (data: TimeEntryCreateDTO) =>
  request<number>({ url: '/execution/time-entry', method: 'POST', data })

export const updateTimeEntry = (data: Partial<TimeEntryVO> & { id: number }) =>
  request<void>({ url: '/execution/time-entry', method: 'PUT', data })

export const approveTimeEntry = (data: TimeEntryApprovalDTO) =>
  request<void>({
    url: '/execution/time-entry/approve',
    method: 'PUT',
    data,
  })

export const rejectTimeEntry = (data: TimeEntryApprovalDTO) =>
  request<void>({
    url: '/execution/time-entry/reject',
    method: 'PUT',
    data,
  })

export const deleteTimeEntry = (id: number) =>
  request<void>({ url: `/execution/time-entry/${id}`, method: 'DELETE' })
