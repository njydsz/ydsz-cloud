import { request } from '@/utils/request'
import type {
  AlertDispatchVO,
  AlertDispatchDTO,
  AlertAggregateVO,
  AlertResolveRolesVO,
} from './types'

/** 提交预警（自动按 level 解析目标角色） */
export const submitAlert = (data: AlertDispatchDTO) =>
  request<number>({ url: '/execution/alert-dispatch', method: 'POST', data })

/** 立即分发 */
export const dispatchAlertNow = (id: number) =>
  request<boolean>({ url: `/execution/alert-dispatch/${id}/dispatch`, method: 'PUT' })

/** 重试失败预警 */
export const retryFailedAlerts = (maxRetry = 3) =>
  request<number>({
    url: '/execution/alert-dispatch/retry',
    method: 'POST',
    params: { maxRetry },
  })

/** 取消预警 */
export const cancelAlert = (id: number, reason?: string) =>
  request<void>({
    url: `/execution/alert-dispatch/${id}/cancel`,
    method: 'PUT',
    params: { reason },
  })

/** 按等级 + 状态查询 */
export const listAlerts = (params: { level?: string; status?: string }) =>
  request<AlertDispatchVO[]>({
    url: '/execution/alert-dispatch/list',
    method: 'GET',
    params,
  })

/** 按类型 × 等级 聚合统计 */
export const aggregateAlerts = (tenantId?: number) =>
  request<AlertAggregateVO[]>({
    url: '/execution/alert-dispatch/aggregate',
    method: 'GET',
    params: { tenantId },
  })

/** 解析等级对应目标角色（黄 → PM/PMO；红 → PMO/GM/CFO） */
export const resolveAlertRoles = (level: string) =>
  request<AlertResolveRolesVO>({
    url: '/execution/alert-dispatch/resolve-roles',
    method: 'GET',
    params: { level },
  })
