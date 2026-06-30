import { request } from '@/utils/request'
import type { ResourcePoolVO, ResourcePoolCreateDTO } from './types'

/** 分页查询 */
export const pageResourcePools = (page: number, size: number, poolType?: string, status?: string) =>
  request<PageResult<ResourcePoolVO>>({
    url: '/resource-pools/page',
    method: 'GET',
    params: { page, size, poolType, status },
  })

/** 按类型查询 */
export const listPoolsByType = (poolType: string) =>
  request<ResourcePoolVO[]>({ url: '/resource-pools/by-type', method: 'GET', params: { poolType } })

/** 按部门查询 */
export const listPoolsByDept = (departmentId: number) =>
  request<ResourcePoolVO[]>({ url: `/resource-pools/by-dept/${departmentId}`, method: 'GET' })

/** 详情 */
export const getResourcePool = (id: number) =>
  request<ResourcePoolVO>({ url: `/resource-pools/${id}`, method: 'GET' })

/** 创建 */
export const createResourcePool = (data: ResourcePoolCreateDTO) =>
  request<number>({ url: '/resource-pools', method: 'POST', data })

/** 更新 */
export const updateResourcePool = (id: number, data: ResourcePoolCreateDTO) =>
  request<void>({ url: `/resource-pools/${id}`, method: 'PUT', data })

/** 删除 */
export const deleteResourcePool = (id: number) =>
  request<void>({ url: `/resource-pools/${id}`, method: 'DELETE' })
