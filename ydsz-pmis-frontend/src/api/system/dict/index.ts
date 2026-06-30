import { request } from '@/utils/request'
import type {
  DictTypeVO,
  DictItemVO,
  DictTypeFormDTO,
  DictItemFormDTO,
  DictItemQuery,
} from './types'

/** 字典类型 */

/** 查询字典类型 */
export const listDictTypes = () =>
  request<DictTypeVO[]>({ url: '/dict/types', method: 'GET' })

/** 字典项 (按 typeCode) */
export const listDictItems = (typeCode: string) =>
  request<DictItemVO[]>({ url: '/dict/items', method: 'GET', params: { typeCode } })

/** 字典项分页 */
export const pageDictItems = (query: DictItemQuery) =>
  request<PageResult<DictItemVO>>({ url: '/dict/items/page', method: 'GET', params: query })

/** 创建字典类型 */
export const createDictType = (data: DictTypeFormDTO) =>
  request<void>({ url: '/dict/types', method: 'POST', data })

/** 更新字典类型 */
export const updateDictType = (typeCode: string, data: DictTypeFormDTO) =>
  request<void>({ url: `/dict/types/${typeCode}`, method: 'PUT', data })

/** 删除字典类型 */
export const deleteDictType = (typeCode: string) =>
  request<void>({ url: `/dict/types/${typeCode}`, method: 'DELETE' })

/** 创建字典项 */
export const createDictItem = (data: DictItemFormDTO) =>
  request<number>({ url: '/dict/items', method: 'POST', data })

/** 更新字典项 */
export const updateDictItem = (id: number, data: DictItemFormDTO) =>
  request<void>({ url: `/dict/items/${id}`, method: 'PUT', data })

/** 删除字典项 */
export const deleteDictItem = (id: number) =>
  request<void>({ url: `/dict/items/${id}`, method: 'DELETE' })

/** 刷新字典缓存 */
export const refreshDictCache = (typeCode: string) =>
  request<void>({ url: '/dict/refresh', method: 'POST', params: { typeCode } })
