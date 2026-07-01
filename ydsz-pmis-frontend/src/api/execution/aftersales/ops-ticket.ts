/**
 * @file 运维工单 API 接口封装
 * @description 提供运维工单的创建、派单、状态变更、关闭评价、SLA 扫描、
 *              分页查询、详情、按项目查询及 SLA 达成率统计等能力，
 *              对应后端 OpsTicketController（/execution/ops-ticket）。
 * @module api/execution/aftersales/ops-ticket
 */
import { request } from '@/utils/request'
import type {
  OpsTicketVO,
  OpsTicketCreateDTO,
  OpsTicketAssignDTO,
  OpsTicketStatusDTO,
} from './types'

/**
 * 创建运维工单
 * @param data 工单创建参数（标题、优先级、关联质保/立项等）
 * @returns 新建工单 ID
 */
export const createOpsTicket = (data: OpsTicketCreateDTO) =>
  request<number>({ url: '/execution/ops-ticket', method: 'POST', data })

/**
 * 派单（指派处理人）
 * @param data 派单参数（工单 ID、指派人 ID、备注）
 * @returns 无返回值
 */
export const assignOpsTicket = (data: OpsTicketAssignDTO) =>
  request<void>({ url: '/execution/ops-ticket/assign', method: 'POST', data })

/**
 * 工单状态变更
 * @param data 状态变更参数（工单 ID、目标状态、处理说明等）
 * @returns 无返回值
 */
export const changeOpsTicketStatus = (data: OpsTicketStatusDTO) =>
  request<void>({ url: '/execution/ops-ticket/status', method: 'POST', data })

/**
 * 关闭工单并评价
 * @param data 关闭评价参数（工单 ID、目标状态、客户评分、客户意见等）
 * @returns 无返回值
 */
export const closeAndEvaluateOpsTicket = (data: OpsTicketStatusDTO) =>
  request<void>({
    url: '/execution/ops-ticket/close-evaluate',
    method: 'POST',
    data,
  })

/**
 * SLA 扫描（标记超时工单）
 * @returns 本次扫描被标记超时的工单数量
 */
export const scanOpsTicketSlaBreaches = () =>
  request<number>({ url: '/execution/ops-ticket/scan/sla', method: 'POST' })

/**
 * 分页查询运维工单
 * @param params 分页与筛选条件（页码、页大小、状态、优先级、立项 ID、处理人 ID、关键字）
 * @returns 工单分页结果
 */
export const pageOpsTickets = (params: {
  page: number
  size: number
  status?: string
  priority?: string
  initiationId?: number
  assigneeId?: number
  keyword?: string
}) =>
  request<PageResult<OpsTicketVO>>({
    url: '/execution/ops-ticket/page',
    method: 'GET',
    params,
  })

/**
 * 查询工单详情
 * @param id 工单 ID
 * @returns 工单详情对象
 */
export const getOpsTicket = (id: number) =>
  request<OpsTicketVO>({ url: `/execution/ops-ticket/${id}`, method: 'GET' })

/**
 * 按立项（项目）查询工单列表
 * @param initiationId 立项 ID
 * @returns 工单列表
 */
export const listOpsTicketsByInitiation = (initiationId: number) =>
  request<OpsTicketVO[]>({
    url: `/execution/ops-ticket/by-initiation/${initiationId}`,
    method: 'GET',
  })

/**
 * SLA 达成率统计
 * @returns 按优先级分组的 SLA 达成率统计列表
 */
export const slaSummaryOpsTicket = () =>
  request<{ priority: string; totalCount: number; responseBreachCount: number; resolveBreachCount: number; responseSlaRate: number; resolveSlaRate: number }[]>({
    url: '/execution/ops-ticket/sla-summary',
    method: 'GET',
  })

/**
 * 按状态聚合工单数量
 * @param initiationId 立项 ID（可选，传入则仅统计该立项下工单）
 * @returns 状态聚合统计列表
 */
export const aggregateOpsTicketByStatus = (initiationId?: number) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/ops-ticket/aggregate/status',
    method: 'GET',
    params: initiationId ? { initiationId } : undefined,
  })
