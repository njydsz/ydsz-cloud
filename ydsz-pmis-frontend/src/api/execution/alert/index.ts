/**
 * @file 预警分发 API 接口封装
 * @description 提供预警提交、立即分发、重试失败预警、取消预警、按等级/状态查询、
 *              类型×等级聚合统计、解析目标角色、驾驶舱 TOP N 等能力，
 *              对应后端 AlertDispatchController（/execution/alert-dispatch）。
 * @module api/execution/alert
 */
import { request } from '@/utils/request'
import type {
  AlertDispatchVO,
  AlertDispatchDTO,
  AlertAggregateVO,
  AlertResolveRolesVO,
} from './types'

/**
 * 提交预警（自动按 level 解析目标角色）
 * @param data 预警提交参数（类型、等级、标题、目标角色、推送渠道等）
 * @returns 新建预警 ID
 */
export const submitAlert = (data: AlertDispatchDTO) =>
  request<number>({ url: '/execution/alert-dispatch', method: 'POST', data })

/**
 * 立即分发预警
 * @param id 预警 ID
 * @returns 是否分发成功
 */
export const dispatchAlertNow = (id: number) =>
  request<boolean>({ url: `/execution/alert-dispatch/${id}/dispatch`, method: 'PUT' })

/**
 * 重试失败的预警分发
 * @param maxRetry 最大重试次数（默认 3）
 * @returns 本次重试处理的预警数量
 */
export const retryFailedAlerts = (maxRetry = 3) =>
  request<number>({
    url: '/execution/alert-dispatch/retry',
    method: 'POST',
    params: { maxRetry },
  })

/**
 * 取消预警
 * @param id 预警 ID
 * @param reason 取消原因（可选）
 * @returns 无返回值
 */
export const cancelAlert = (id: number, reason?: string) =>
  request<void>({
    url: `/execution/alert-dispatch/${id}/cancel`,
    method: 'PUT',
    params: { reason },
  })

/**
 * 按等级 + 状态查询预警列表
 * @param params 查询条件（等级、状态）
 * @returns 预警分发列表
 */
export const listAlerts = (params: { level?: string; status?: string }) =>
  request<AlertDispatchVO[]>({
    url: '/execution/alert-dispatch/list',
    method: 'GET',
    params,
  })

/**
 * 按类型 × 等级聚合统计预警
 * @param tenantId 租户 ID（可选）
 * @returns 类型×等级聚合统计列表
 */
export const aggregateAlerts = (tenantId?: number) =>
  request<AlertAggregateVO[]>({
    url: '/execution/alert-dispatch/aggregate',
    method: 'GET',
    params: { tenantId },
  })

/**
 * 解析等级对应目标角色（黄 → PM/PMO；红 → PMO/GM/CFO）
 * @param level 预警等级（YELLOW/RED/NORMAL）
 * @returns 等级与对应目标角色集合
 */
export const resolveAlertRoles = (level: string) =>
  request<AlertResolveRolesVO>({
    url: '/execution/alert-dispatch/resolve-roles',
    method: 'GET',
    params: { level },
  })

/**
 * 仪表盘 - 预警项目 TOP N（批次 21 / P2 dashboard）
 * @param period 统计周期
 * @param topN 取前 N 条（默认 5）
 * @returns 项目预警 TOP N 列表
 */
export const getCockpitAlertTopN = (period: string, topN = 5) =>
  request<Array<{ projectCode: string; projectName: string; alertLevel: 'RED' | 'YELLOW' | 'NORMAL'; alertCount: number }>>({
    url: '/execution/alert-dispatch/cockpit-top-n',
    method: 'GET',
    params: { period, topN },
  })
