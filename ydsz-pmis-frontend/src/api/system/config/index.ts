/**
 * @file 系统参数配置 API
 * @description 提供系统参数配置的增删改查、分组批量操作、缓存刷新等接口；
 *              对应后端 SysConfigController（/configs）。
 * @module api/system/config
 */

import { request } from '@/utils/request'
import type { ConfigVO, ConfigFormDTO, ConfigQuery } from './types'

/**
 * 配置分页查询
 * @param query 分页及过滤条件
 * @returns 配置分页结果
 */
export const pageConfigs = (query: ConfigQuery) =>
  request<PageResult<ConfigVO>>({ url: '/configs', method: 'GET', params: query })

/**
 * 按 group + key 查配置
 * @param group 配置分组
 * @param key   配置键
 * @returns 配置详情
 */
export const getConfigByKey = (group: string, key: string) =>
  request<ConfigVO>({ url: '/configs/by-key', method: 'GET', params: { group, key } })

/**
 * 按 group 查全部配置（key-value 形式）
 * @param group 配置分组
 * @returns 该分组下所有配置键值对
 */
export const getGroupConfigs = (group: string) =>
  request<Record<string, string>>({ url: `/configs/group/${group}`, method: 'GET' })

/**
 * 公开配置（前端可见）
 * @returns 公开配置列表
 */
export const listPublicConfigs = () =>
  request<ConfigVO[]>({ url: '/configs/public', method: 'GET' })

/**
 * 创建配置
 * @param data 配置表单数据
 * @returns 新建配置 ID
 */
export const createConfig = (data: ConfigFormDTO) =>
  request<number>({ url: '/configs', method: 'POST', data })

/**
 * 更新配置
 * @param data 配置表单数据（必须含 id）
 * @returns void
 */
export const updateConfig = (data: ConfigFormDTO) =>
  request<void>({ url: '/configs', method: 'PUT', data })

/**
 * 删除配置
 * @param id 配置 ID
 * @returns void
 */
export const deleteConfig = (id: number) =>
  request<void>({ url: `/configs/${id}`, method: 'DELETE' })

/**
 * 按分组批量删除
 * @param group 配置分组
 * @returns 删除条数
 */
export const deleteByGroup = (group: string) =>
  request<number>({ url: `/configs/group/${group}`, method: 'DELETE' })

/**
 * 按分组批量启停
 * @param group  配置分组
 * @param status 目标状态：ENABLED 启用 / DISABLED 禁用
 * @returns 受影响条数
 */
export const updateStatusByGroup = (group: string, status: 'ENABLED' | 'DISABLED') =>
  request<number>({ url: `/configs/group/${group}/status/${status}`, method: 'PUT' })

/**
 * 刷新缓存
 * @returns void
 */
export const refreshConfigCache = () =>
  request<void>({ url: '/configs/refresh', method: 'POST' })
