/**
 * @file 交付物管理 API
 * @description 提供项目交付物（Delivery Item）相关的接口调用，
 *              包含分页查询、详情、新建及状态变更等能力。
 *              对应后端 Controller：DeliveryItemController（/execution/delivery）。
 * @module api/execution/delivery
 */
import { request } from '@/utils/request'
import type { DeliveryItemVO, DeliveryItemCreateDTO, DeliveryItemStatusDTO } from './types'

/**
 * 分页查询交付物列表
 * @param page 当前页码（从 1 开始）
 * @param size 每页条数
 * @param params 可选过滤条件：keyword 关键字、status 状态、initiationId 立项ID、stage 阶段
 * @returns 交付物分页结果
 */
export const pageDeliveryItems = (
  page: number,
  size: number,
  params?: { keyword?: string; status?: string; initiationId?: number; stage?: string },
) =>
  request<PageResult<DeliveryItemVO>>({
    url: '/execution/delivery/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/**
 * 查询交付物详情
 * @param id 交付物ID
 * @returns 交付物详情
 */
export const getDeliveryItem = (id: number) =>
  request<DeliveryItemVO>({
    url: `/execution/delivery/${id}`,
    method: 'GET',
  })

/**
 * 新建交付物
 * @param data 交付物创建 DTO
 * @returns 新建交付物ID
 */
export const createDeliveryItem = (data: DeliveryItemCreateDTO) =>
  request<number>({
    url: '/execution/delivery',
    method: 'POST',
    data,
  })

/**
 * 变更交付物状态（提交/验收通过/驳回/豁免）
 * @param data 交付物状态变更 DTO
 * @returns 无返回值
 */
export const changeDeliveryItemStatus = (data: DeliveryItemStatusDTO) =>
  request<void>({
    url: '/execution/delivery/status',
    method: 'PUT',
    data,
  })
