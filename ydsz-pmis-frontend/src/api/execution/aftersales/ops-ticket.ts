import { request } from '@/utils/request'
import type {
  OpsTicketVO,
  OpsTicketCreateDTO,
  OpsTicketAssignDTO,
  OpsTicketStatusDTO,
} from './types'

/** 创建工单 */
export const createOpsTicket = (data: OpsTicketCreateDTO) =>
  request<number>({ url: '/execution/ops-ticket', method: 'POST', data })

/** 派单 */
export const assignOpsTicket = (data: OpsTicketAssignDTO) =>
  request<void>({ url: '/execution/ops-ticket/assign', method: 'POST', data })

/** 状态变更 */
export const changeOpsTicketStatus = (data: OpsTicketStatusDTO) =>
  request<void>({ url: '/execution/ops-ticket/status', method: 'POST', data })

/** 关闭工单并评价 */
export const closeAndEvaluateOpsTicket = (data: OpsTicketStatusDTO) =>
  request<void>({
    url: '/execution/ops-ticket/close-evaluate',
    method: 'POST',
    data,
  })

/** SLA 扫描（标记超时工单） */
export const scanOpsTicketSlaBreaches = () =>
  request<number>({ url: '/execution/ops-ticket/scan/sla', method: 'POST' })

/** 分页查询 */
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

/** 详情 */
export const getOpsTicket = (id: number) =>
  request<OpsTicketVO>({ url: `/execution/ops-ticket/${id}`, method: 'GET' })

/** 按项目查询 */
export const listOpsTicketsByInitiation = (initiationId: number) =>
  request<OpsTicketVO[]>({
    url: `/execution/ops-ticket/by-initiation/${initiationId}`,
    method: 'GET',
  })

/** SLA 达成率统计 */
export const slaSummaryOpsTicket = () =>
  request<{ priority: string; totalCount: number; responseBreachCount: number; resolveBreachCount: number; responseSlaRate: number; resolveSlaRate: number }[]>({
    url: '/execution/ops-ticket/sla-summary',
    method: 'GET',
  })

/** 按状态聚合 */
export const aggregateOpsTicketByStatus = (initiationId?: number) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/ops-ticket/aggregate/status',
    method: 'GET',
    params: initiationId ? { initiationId } : undefined,
  })
