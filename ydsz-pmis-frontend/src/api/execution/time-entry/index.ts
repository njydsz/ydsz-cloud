/**
 * @file 工时填报管理 API
 * @description 提供项目执行阶段员工工时填报的增删改查、审批与驳回能力，
 *              对应后端 TimeEntryController（/execution/time-entry）。
 * @module api/execution/time-entry
 */
import { request } from '@/utils/request'
import type { TimeEntryVO, TimeEntryCreateDTO, TimeEntryApprovalDTO } from './types'

/**
 * 分页查询工时填报记录
 * @param page 当前页码（从 1 开始）
 * @param size 每页条数
 * @param params 可选查询条件：关键字、状态、员工 ID、立项 ID、起止日期
 * @returns 工时填报分页结果
 */
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

/**
 * 根据 ID 获取工时填报详情
 * @param id 工时记录 ID
 * @returns 工时填报详情
 */
export const getTimeEntry = (id: number) =>
  request<TimeEntryVO>({ url: `/execution/time-entry/${id}`, method: 'GET' })

/**
 * 新建工时填报记录
 * @param data 工时创建参数
 * @returns 新建工时记录的 ID
 */
export const createTimeEntry = (data: TimeEntryCreateDTO) =>
  request<number>({ url: '/execution/time-entry', method: 'POST', data })

/**
 * 更新工时填报记录
 * @param data 工时更新参数（必须包含 id）
 * @returns 无返回值
 */
export const updateTimeEntry = (data: Partial<TimeEntryVO> & { id: number }) =>
  request<void>({ url: '/execution/time-entry', method: 'PUT', data })

/**
 * 审批通过工时填报记录
 * @param data 审批参数
 * @returns 无返回值
 */
export const approveTimeEntry = (data: TimeEntryApprovalDTO) =>
  request<void>({
    url: '/execution/time-entry/approve',
    method: 'PUT',
    data,
  })

/**
 * 驳回工时填报记录
 * @param data 驳回参数（含驳回原因）
 * @returns 无返回值
 */
export const rejectTimeEntry = (data: TimeEntryApprovalDTO) =>
  request<void>({
    url: '/execution/time-entry/reject',
    method: 'PUT',
    data,
  })

/**
 * 根据 ID 删除工时填报记录
 * @param id 工时记录 ID
 * @returns 无返回值
 */
export const deleteTimeEntry = (id: number) =>
  request<void>({ url: `/execution/time-entry/${id}`, method: 'DELETE' })
