import { request } from '@/utils/request'
import type { SatisfactionVO, SatisfactionCreateDTO } from './types'

/** 提交满意度评价 */
export const submitSatisfaction = (data: SatisfactionCreateDTO) =>
  request<number>({ url: '/execution/satisfaction', method: 'POST', data })

/** 标记跟进 */
export const markFollowUp = (id: number, note?: string) =>
  request<void>({
    url: '/execution/satisfaction/follow-up',
    method: 'POST',
    params: { id, note },
  })

/** 关闭跟进 */
export const closeFollowUp = (id: number) =>
  request<void>({
    url: '/execution/satisfaction/follow-up/close',
    method: 'POST',
    params: { id },
  })

/** 整体满意度均值 */
export const overallSatisfaction = () =>
  request<Record<string, unknown>>({
    url: '/execution/satisfaction/overall',
    method: 'GET',
  })

/** 等级分布 */
export const levelDistributionSatisfaction = () =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/satisfaction/level-distribution',
    method: 'GET',
  })

/** 分页查询满意度评价 */
export const pageSatisfactions = (params: {
  page: number
  size: number
  ticketId?: number
  initiationId?: number
  followUpStatus?: string
  level?: string
  keyword?: string
}) =>
  request<PageResult<SatisfactionVO>>({
    url: '/execution/satisfaction/page',
    method: 'GET',
    params,
  })
