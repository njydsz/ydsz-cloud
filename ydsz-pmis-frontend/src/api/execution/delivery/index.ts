import { request } from '@/utils/request'
import type { DeliveryItemVO, DeliveryItemCreateDTO, DeliveryItemStatusDTO } from './types'

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

export const getDeliveryItem = (id: number) =>
  request<DeliveryItemVO>({
    url: `/execution/delivery/${id}`,
    method: 'GET',
  })

export const createDeliveryItem = (data: DeliveryItemCreateDTO) =>
  request<number>({
    url: '/execution/delivery',
    method: 'POST',
    data,
  })

export const changeDeliveryItemStatus = (data: DeliveryItemStatusDTO) =>
  request<void>({
    url: '/execution/delivery/status',
    method: 'PUT',
    data,
  })
