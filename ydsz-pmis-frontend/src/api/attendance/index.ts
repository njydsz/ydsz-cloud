/**
 * @file 考勤管理 API
 * @description 提供出勤打卡记录、加班申请/审批、请假申请/审批等能力，
 *              对应后端 AttendanceController（/attendance/**）。
 * @module api/attendance
 */
import { request } from '@/utils/request'
import type { AttendanceCreateDTO, AttendanceVO, LeaveCreateDTO, LeaveVO, OvertimeCreateDTO, OvertimeVO } from './types'

// ============== 出勤 ==============

/**
 * 记录出勤
 *
 * 录入员工某日出勤打卡信息（上下班时间、状态等）。
 *
 * @param data 出勤记录入参
 * @returns 新建出勤记录 ID
 */
export const recordAttendance = (data: AttendanceCreateDTO) =>
  request<number>({ url: '/attendance/record', method: 'POST', data })

/**
 * 出勤记录分页查询
 *
 * 按员工、日期区间筛选出勤记录并分页返回。
 *
 * @param params 查询条件（employeeId / startDate / endDate / page / size）
 * @returns 出勤记录分页结果
 */
export const pageAttendance = (params: { employeeId?: number; startDate?: string; endDate?: string; page: number; size: number }) =>
  request<PageResult<AttendanceVO>>({ url: '/attendance/record/page', method: 'GET', params })

/**
 * 按状态统计出勤
 *
 * 按员工、日期区间统计各出勤状态（正常/迟到/早退/缺勤等）数量。
 *
 * @param params 统计条件（employeeId / startDate / endDate）
 * @returns 状态统计结果数组
 */
export const statByStatus = (params: { employeeId?: number; startDate?: string; endDate?: string }) =>
  request<Array<Record<string, unknown>>>({ url: '/attendance/record/stat', method: 'GET', params })

// ============== 加班 ==============

/**
 * 提交加班申请
 *
 * 员工提交加班申请单，进入待审批流程。
 *
 * @param data 加班申请入参
 * @returns 新建加班申请 ID
 */
export const submitOvertime = (data: OvertimeCreateDTO) =>
  request<number>({ url: '/attendance/overtime', method: 'POST', data })

/**
 * 审批加班申请
 *
 * 审批人对指定加班申请进行通过/驳回操作。
 *
 * @param id 加班申请 ID
 * @param action 审批动作（APPROVE / REJECT）
 * @param remark 可选审批备注
 * @returns void
 */
export const approveOvertime = (id: number, action: string, remark?: string) =>
  request<void>({ url: `/attendance/overtime/${id}/approve`, method: 'POST', params: { action, remark } })

/**
 * 加班申请分页查询
 *
 * 按员工、审批状态筛选加班申请并分页返回。
 *
 * @param params 查询条件（employeeId / approvalStatus / page / size）
 * @returns 加班申请分页结果
 */
export const pageOvertime = (params: { employeeId?: number; approvalStatus?: string; page: number; size: number }) =>
  request<PageResult<OvertimeVO>>({ url: '/attendance/overtime/page', method: 'GET', params })

/**
 * 查询加班申请详情
 *
 * 按 ID 查询单条加班申请详情。
 *
 * @param id 加班申请 ID
 * @returns 加班申请详情
 */
export const getOvertime = (id: number) =>
  request<OvertimeVO>({ url: `/attendance/overtime/${id}`, method: 'GET' })

// ============== 请假 ==============

/**
 * 提交请假申请
 *
 * 员工提交请假申请单，进入待审批流程。
 *
 * @param data 请假申请入参
 * @returns 新建请假申请 ID
 */
export const submitLeave = (data: LeaveCreateDTO) =>
  request<number>({ url: '/attendance/leave', method: 'POST', data })

/**
 * 审批请假申请
 *
 * 审批人对指定请假申请进行通过/驳回操作。
 *
 * @param id 请假申请 ID
 * @param action 审批动作（APPROVE / REJECT）
 * @param remark 可选审批备注
 * @returns void
 */
export const approveLeave = (id: number, action: string, remark?: string) =>
  request<void>({ url: `/attendance/leave/${id}/approve`, method: 'POST', params: { action, remark } })

/**
 * 请假申请分页查询
 *
 * 按员工、审批状态筛选请假申请并分页返回。
 *
 * @param params 查询条件（employeeId / approvalStatus / page / size）
 * @returns 请假申请分页结果
 */
export const pageLeave = (params: { employeeId?: number; approvalStatus?: string; page: number; size: number }) =>
  request<PageResult<LeaveVO>>({ url: '/attendance/leave/page', method: 'GET', params })

/**
 * 查询请假申请详情
 *
 * 按 ID 查询单条请假申请详情。
 *
 * @param id 请假申请 ID
 * @returns 请假申请详情
 */
export const getLeave = (id: number) =>
  request<LeaveVO>({ url: `/attendance/leave/${id}`, method: 'GET' })
