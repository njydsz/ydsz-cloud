/**
 * @file 报表订阅 API 封装
 * @description 对接后端 ReportSubscriptionController（/report/subscription），
 *              提供报表订阅的创建、查询、暂停/恢复、删除与执行历史查询能力。
 * @module api/report/subscription
 */
import { request } from '@/utils/request'

/** 订阅状态 */
export type SubscriptionStatus = 'ACTIVE' | 'PAUSED'

/** 投递渠道 */
export type DeliveryChannel = 'EMAIL' | 'DINGTALK' | 'WECHAT_WORK' | 'WEBHOOK'

/** 报表订阅记录 */
export interface ReportSubscription {
  /** 订阅 ID */
  id: string
  /** 报表类型 */
  reportType: string
  /** 报表名称 */
  reportName: string
  /** Cron 表达式 */
  cronExpression: string
  /** 投递渠道（逗号分隔） */
  deliveryChannels: string
  /** 投递邮箱（逗号分隔） */
  deliveryEmails?: string
  /** 报表参数（JSON） */
  params: string
  /** 状态 */
  status: SubscriptionStatus
  /** 上次执行时间 */
  lastRunAt?: string
  /** 上次执行状态 */
  lastRunStatus?: string
  /** 创建时间 */
  createdAt: string
  /** 更新时间 */
  updatedAt?: string
}

/** 创建报表订阅参数 */
export interface CreateSubscriptionParams {
  /** 报表类型 */
  reportType: string
  /** 报表名称 */
  reportName: string
  /** Cron 表达式 */
  cronExpression: string
  /** 投递渠道（逗号分隔） */
  deliveryChannels: string
  /** 投递邮箱（逗号分隔） */
  deliveryEmails?: string
  /** 报表参数（JSON） */
  params: string
}

/** 订阅执行历史记录 */
export interface SubscriptionHistory {
  /** 记录 ID */
  id: string
  /** 订阅 ID */
  subscriptionId: string
  /** 执行时间 */
  runAt: string
  /** 执行状态 */
  runStatus: string
  /** 文件 URL */
  fileUrl?: string
  /** 错误信息 */
  errorMessage?: string
  /** 执行时长（毫秒） */
  durationMs?: number
  /** 文件大小 */
  fileSize?: number
}

/**
 * 创建报表订阅
 * @param params 订阅参数
 * @returns 订阅 ID
 */
export const createSubscription = (params: CreateSubscriptionParams) =>
  request<string>({
    url: '/api/project/report/subscription',
    method: 'POST',
    data: params,
  })

/**
 * 查询当前用户的订阅列表
 * @returns 订阅列表
 */
export const getSubscriptionList = () =>
  request<ReportSubscription[]>({
    url: '/api/project/report/subscription/list',
    method: 'GET',
  })

/**
 * 暂停/恢复订阅
 * @param id 订阅 ID
 * @param status 目标状态
 */
export const toggleSubscriptionStatus = (id: string, status: SubscriptionStatus) =>
  request<void>({
    url: `/api/project/report/subscription/${id}/status`,
    method: 'PUT',
    params: { status },
  })

/**
 * 删除订阅（软删除）
 * @param id 订阅 ID
 */
export const deleteSubscription = (id: string) =>
  request<void>({
    url: `/api/project/report/subscription/${id}`,
    method: 'DELETE',
  })

/**
 * 查询订阅执行历史
 * @param id 订阅 ID
 * @param page 页码
 * @param size 每页条数
 * @returns 历史记录列表
 */
export const getSubscriptionHistory = (id: string, page = 1, size = 20) =>
  request<SubscriptionHistory[]>({
    url: `/api/project/report/subscription/${id}/history`,
    method: 'GET',
    params: { page, size },
  })
