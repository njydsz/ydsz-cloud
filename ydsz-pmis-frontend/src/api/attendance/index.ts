import { request } from '@/utils/request'
import type { AttendanceCreateDTO, AttendanceVO, LeaveCreateDTO, LeaveVO, OvertimeCreateDTO, OvertimeVO } from './types'

// ============== 出勤 ==============

export const recordAttendance = (data: AttendanceCreateDTO) =>
  request<number>({ url: '/attendance/record', method: 'POST', data })

export const pageAttendance = (params: { employeeId?: number; startDate?: string; endDate?: string; page: number; size: number }) =>
  request<PageResult<AttendanceVO>>({ url: '/attendance/record/page', method: 'GET', params })

export const statByStatus = (params: { employeeId?: number; startDate?: string; endDate?: string }) =>
  request<Array<Record<string, unknown>>>({ url: '/attendance/record/stat', method: 'GET', params })

// ============== 加班 ==============

export const submitOvertime = (data: OvertimeCreateDTO) =>
  request<number>({ url: '/attendance/overtime', method: 'POST', data })

export const approveOvertime = (id: number, action: string, remark?: string) =>
  request<void>({ url: `/attendance/overtime/${id}/approve`, method: 'POST', params: { action, remark } })

export const pageOvertime = (params: { employeeId?: number; approvalStatus?: string; page: number; size: number }) =>
  request<PageResult<OvertimeVO>>({ url: '/attendance/overtime/page', method: 'GET', params })

export const getOvertime = (id: number) =>
  request<OvertimeVO>({ url: `/attendance/overtime/${id}`, method: 'GET' })

// ============== 请假 ==============

export const submitLeave = (data: LeaveCreateDTO) =>
  request<number>({ url: '/attendance/leave', method: 'POST', data })

export const approveLeave = (id: number, action: string, remark?: string) =>
  request<void>({ url: `/attendance/leave/${id}/approve`, method: 'POST', params: { action, remark } })

export const pageLeave = (params: { employeeId?: number; approvalStatus?: string; page: number; size: number }) =>
  request<PageResult<LeaveVO>>({ url: '/attendance/leave/page', method: 'GET', params })

export const getLeave = (id: number) =>
  request<LeaveVO>({ url: `/attendance/leave/${id}`, method: 'GET' })
