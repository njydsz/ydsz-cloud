/**
 * @file 资源池 API 接口封装
 * @description 资源池（ResourcePool）相关接口，对应后端 ResourcePoolController（/resource-pools）。提供分页、按类型/部门查询、详情、创建、更新、删除等能力。
 * @module api/resource/pool
 */
import { request } from '@/utils/request'
import type { ResourcePoolVO, ResourcePoolCreateDTO } from './types'

/**
 * 分页查询资源池
 * @param page 页码
 * @param size 每页条数
 * @param poolType 资源池类型（可选，HEADQUARTER/DIVISION/BACKUP）
 * @param status 状态（可选）
 * @returns 分页结果
 */
export const pageResourcePools = (page: number, size: number, poolType?: string, status?: string) =>
  request<PageResult<ResourcePoolVO>>({
    url: '/resource-pools/page',
    method: 'GET',
    params: { page, size, poolType, status },
  })

/**
 * 按类型查询资源池列表
 * @param poolType 资源池类型（HEADQUARTER/DIVISION/BACKUP）
 * @returns 资源池列表
 */
export const listPoolsByType = (poolType: string) =>
  request<ResourcePoolVO[]>({ url: '/resource-pools/by-type', method: 'GET', params: { poolType } })

/**
 * 按部门查询资源池列表
 * @param departmentId 部门 ID
 * @returns 资源池列表
 */
export const listPoolsByDept = (departmentId: number) =>
  request<ResourcePoolVO[]>({ url: `/resource-pools/by-dept/${departmentId}`, method: 'GET' })

/**
 * 查询资源池详情
 * @param id 资源池 ID
 * @returns 资源池详情
 */
export const getResourcePool = (id: number) =>
  request<ResourcePoolVO>({ url: `/resource-pools/${id}`, method: 'GET' })

/**
 * 创建资源池
 * @param data 资源池创建参数
 * @returns 新建资源池 ID
 */
export const createResourcePool = (data: ResourcePoolCreateDTO) =>
  request<number>({ url: '/resource-pools', method: 'POST', data })

/**
 * 更新资源池
 * @param id 资源池 ID
 * @param data 资源池更新参数
 * @returns 无返回值
 */
export const updateResourcePool = (id: number, data: ResourcePoolCreateDTO) =>
  request<void>({ url: `/resource-pools/${id}`, method: 'PUT', data })

/**
 * 删除资源池
 * @param id 资源池 ID
 * @returns 无返回值
 */
export const deleteResourcePool = (id: number) =>
  request<void>({ url: `/resource-pools/${id}`, method: 'DELETE' })
