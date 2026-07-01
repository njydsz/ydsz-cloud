/**
 * @file 质保期管理 API 接口封装
 * @description 提供质保期的创建、终止、即将到期/已过期扫描、分页查询、
 *              详情查询、即将到期列表等能力，对应后端 WarrantyController（/execution/warranty）。
 * @module api/execution/aftersales/warranty
 */
import { request } from '@/utils/request'
import type {
  WarrantyVO,
  WarrantyCreateDTO,
  WarrantyTerminateDTO,
} from './types'

/**
 * 创建质保期
 * @param data 质保期创建参数（立项 ID、时长、开始日期、提醒天数等）
 * @returns 新建质保期 ID
 */
export const createWarranty = (data: WarrantyCreateDTO) =>
  request<number>({ url: '/execution/warranty', method: 'POST', data })

/**
 * 终止质保期
 * @param data 终止参数（质保期 ID、终止原因）
 * @returns 无返回值
 */
export const terminateWarranty = (data: WarrantyTerminateDTO) =>
  request<void>({
    url: '/execution/warranty/terminate',
    method: 'POST',
    data,
  })

/**
 * 扫描即将到期的质保期（提前 noticeDays 天）
 * @param noticeDays 提前提醒天数（默认 30）
 * @returns 命中的即将到期质保期数量
 */
export const scanExpiringWarranty = (noticeDays = 30) =>
  request<number>({
    url: '/execution/warranty/scan/expiring',
    method: 'POST',
    params: { noticeDays },
  })

/**
 * 扫描已过期的质保期
 * @returns 命中的已过期质保期数量
 */
export const scanOverdueWarranty = () =>
  request<number>({ url: '/execution/warranty/scan/overdue', method: 'POST' })

/**
 * 分页查询质保期
 * @param params 分页与筛选条件（页码、页大小、状态、立项 ID、关键字）
 * @returns 质保期分页结果
 */
export const pageWarranties = (params: {
  page: number
  size: number
  status?: string
  initiationId?: number
  keyword?: string
}) =>
  request<PageResult<WarrantyVO>>({
    url: '/execution/warranty/page',
    method: 'GET',
    params,
  })

/**
 * 查询质保期详情
 * @param id 质保期 ID
 * @returns 质保期详情对象
 */
export const getWarranty = (id: number) =>
  request<WarrantyVO>({ url: `/execution/warranty/${id}`, method: 'GET' })

/**
 * 查询即将到期的质保期列表
 * @param until 截止日期（YYYY-MM-DD，可选）
 * @returns 即将到期的质保期列表
 */
export const listExpiringWarranty = (until?: string) =>
  request<WarrantyVO[]>({
    url: '/execution/warranty/expiring',
    method: 'GET',
    params: { until },
  })
