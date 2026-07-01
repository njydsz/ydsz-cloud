/**
 * @file 字典管理 API
 * @description 提供字典类型与字典项的增删改查、分页查询、缓存刷新等接口；
 *              对应后端 DictController（/dict）。
 * @module api/system/dict
 */

import { request } from '@/utils/request'
import type {
  DictTypeVO,
  DictItemVO,
  DictTypeFormDTO,
  DictItemFormDTO,
  DictItemQuery,
} from './types'

/** 字典类型 */

/**
 * 查询字典类型
 * @returns 全量字典类型列表
 */
export const listDictTypes = () =>
  request<DictTypeVO[]>({ url: '/dict/types', method: 'GET' })

/**
 * 字典项 (按 typeCode)
 * @param typeCode 字典类型编码
 * @returns 该字典类型下的全部字典项
 */
export const listDictItems = (typeCode: string) =>
  request<DictItemVO[]>({ url: '/dict/items', method: 'GET', params: { typeCode } })

/**
 * 字典项分页
 * @param query 分页及过滤条件
 * @returns 字典项分页结果
 */
export const pageDictItems = (query: DictItemQuery) =>
  request<PageResult<DictItemVO>>({ url: '/dict/items/page', method: 'GET', params: query })

/**
 * 创建字典类型
 * @param data 字典类型表单数据
 * @returns void
 */
export const createDictType = (data: DictTypeFormDTO) =>
  request<void>({ url: '/dict/types', method: 'POST', data })

/**
 * 更新字典类型
 * @param typeCode 字典类型编码
 * @param data     字典类型表单数据
 * @returns void
 */
export const updateDictType = (typeCode: string, data: DictTypeFormDTO) =>
  request<void>({ url: `/dict/types/${typeCode}`, method: 'PUT', data })

/**
 * 删除字典类型
 * @param typeCode 字典类型编码
 * @returns void
 */
export const deleteDictType = (typeCode: string) =>
  request<void>({ url: `/dict/types/${typeCode}`, method: 'DELETE' })

/**
 * 创建字典项
 * @param data 字典项表单数据
 * @returns 新建字典项 ID
 */
export const createDictItem = (data: DictItemFormDTO) =>
  request<number>({ url: '/dict/items', method: 'POST', data })

/**
 * 更新字典项
 * @param id   字典项 ID
 * @param data 字典项表单数据
 * @returns void
 */
export const updateDictItem = (id: number, data: DictItemFormDTO) =>
  request<void>({ url: `/dict/items/${id}`, method: 'PUT', data })

/**
 * 删除字典项
 * @param id 字典项 ID
 * @returns void
 */
export const deleteDictItem = (id: number) =>
  request<void>({ url: `/dict/items/${id}`, method: 'DELETE' })

/**
 * 刷新字典缓存
 * @param typeCode 字典类型编码
 * @returns void
 */
export const refreshDictCache = (typeCode: string) =>
  request<void>({ url: '/dict/refresh', method: 'POST', params: { typeCode } })
