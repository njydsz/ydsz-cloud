/**
 * @file 客户满意度评价 API 接口封装
 * @description 提供满意度评价提交、跟进标记/关闭、整体均值、等级分布、
 *              分页查询等能力，对应后端 SatisfactionController（/execution/satisfaction）。
 * @module api/execution/aftersales/satisfaction
 */
import { request } from '@/utils/request'
import type { SatisfactionVO, SatisfactionCreateDTO } from './types'

/**
 * 提交客户满意度评价
 * @param data 满意度评价参数（关联工单/立项、各维度评分、客户评论等）
 * @returns 新建满意度评价 ID
 */
export const submitSatisfaction = (data: SatisfactionCreateDTO) =>
  request<number>({ url: '/execution/satisfaction', method: 'POST', data })

/**
 * 标记满意度评价为需跟进
 * @param id 满意度评价 ID
 * @param note 跟进备注（可选）
 * @returns 无返回值
 */
export const markFollowUp = (id: number, note?: string) =>
  request<void>({
    url: '/execution/satisfaction/follow-up',
    method: 'POST',
    params: { id, note },
  })

/**
 * 关闭满意度评价的跟进状态
 * @param id 满意度评价 ID
 * @returns 无返回值
 */
export const closeFollowUp = (id: number) =>
  request<void>({
    url: '/execution/satisfaction/follow-up/close',
    method: 'POST',
    params: { id },
  })

/**
 * 查询整体满意度均值
 * @returns 各维度评分均值统计对象
 */
export const overallSatisfaction = () =>
  request<Record<string, unknown>>({
    url: '/execution/satisfaction/overall',
    method: 'GET',
  })

/**
 * 查询满意度等级分布
 * @returns 按等级分组的统计列表
 */
export const levelDistributionSatisfaction = () =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/satisfaction/level-distribution',
    method: 'GET',
  })

/**
 * 分页查询满意度评价
 * @param params 分页与筛选条件（页码、页大小、工单 ID、立项 ID、跟进状态、等级、关键字）
 * @returns 满意度评价分页结果
 */
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
