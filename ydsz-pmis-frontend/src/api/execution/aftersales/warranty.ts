import { request } from '@/utils/request'
import type {
  WarrantyVO,
  WarrantyCreateDTO,
  WarrantyTerminateDTO,
} from './types'

/** 创建质保期 */
export const createWarranty = (data: WarrantyCreateDTO) =>
  request<number>({ url: '/execution/warranty', method: 'POST', data })

/** 终止质保期 */
export const terminateWarranty = (data: WarrantyTerminateDTO) =>
  request<void>({
    url: '/execution/warranty/terminate',
    method: 'POST',
    data,
  })

/** 扫描即将到期（提前 noticeDays 天） */
export const scanExpiringWarranty = (noticeDays = 30) =>
  request<number>({
    url: '/execution/warranty/scan/expiring',
    method: 'POST',
    params: { noticeDays },
  })

/** 扫描已过期 */
export const scanOverdueWarranty = () =>
  request<number>({ url: '/execution/warranty/scan/overdue', method: 'POST' })

/** 分页查询 */
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

/** 详情 */
export const getWarranty = (id: number) =>
  request<WarrantyVO>({ url: `/execution/warranty/${id}`, method: 'GET' })

/** 即将到期列表 */
export const listExpiringWarranty = (until?: string) =>
  request<WarrantyVO[]>({
    url: '/execution/warranty/expiring',
    method: 'GET',
    params: { until },
  })
