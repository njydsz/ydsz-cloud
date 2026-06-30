import { request } from '@/utils/request'
import type { ConfigVO, ConfigFormDTO, ConfigQuery } from './types'

/** 配置分页查询 */
export const pageConfigs = (query: ConfigQuery) =>
  request<PageResult<ConfigVO>>({ url: '/configs', method: 'GET', params: query })

/** 按 group + key 查配置 */
export const getConfigByKey = (group: string, key: string) =>
  request<ConfigVO>({ url: '/configs/by-key', method: 'GET', params: { group, key } })

/** 按 group 查全部配置（key-value 形式） */
export const getGroupConfigs = (group: string) =>
  request<Record<string, string>>({ url: `/configs/group/${group}`, method: 'GET' })

/** 公开配置（前端可见） */
export const listPublicConfigs = () =>
  request<ConfigVO[]>({ url: '/configs/public', method: 'GET' })

/** 创建配置 */
export const createConfig = (data: ConfigFormDTO) =>
  request<number>({ url: '/configs', method: 'POST', data })

/** 更新配置 */
export const updateConfig = (data: ConfigFormDTO) =>
  request<void>({ url: '/configs', method: 'PUT', data })

/** 删除配置 */
export const deleteConfig = (id: number) =>
  request<void>({ url: `/configs/${id}`, method: 'DELETE' })

/** 按分组批量删除 */
export const deleteByGroup = (group: string) =>
  request<number>({ url: `/configs/group/${group}`, method: 'DELETE' })

/** 按分组批量启停 */
export const updateStatusByGroup = (group: string, status: 'ENABLED' | 'DISABLED') =>
  request<number>({ url: `/configs/group/${group}/status/${status}`, method: 'PUT' })

/** 刷新缓存 */
export const refreshConfigCache = () =>
  request<void>({ url: '/configs/refresh', method: 'POST' })
