/**
 * @file 回收站 API
 * @description 提供回收站文件的查询、恢复、彻底删除、清空能力，
 *              对应后端 TrashController（/nextwiki/trash）。
 * @module api/nextwiki/trash
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { TrashItemVO } from './types'

/**
 * 分页查询回收站文件列表
 * @param page 当前页码
 * @param size 每页条数
 * @returns 回收站条目分页结果
 */
export const listTrash = (page: number, size: number) =>
  request<PageResult<TrashItemVO>>({
    url: '/nextwiki/trash/list',
    method: 'GET',
    params: { page, size },
  })

/**
 * 从回收站恢复文件
 * @param id 回收站条目 ID
 */
export const restoreFromTrash = (id: string) =>
  request<void>({ url: `/nextwiki/trash/${id}/restore`, method: 'POST' })

/**
 * 彻底删除回收站文件（不可恢复）
 * @param id 回收站条目 ID
 */
export const purgeFromTrash = (id: string) =>
  request<void>({ url: `/nextwiki/trash/${id}`, method: 'DELETE' })

/**
 * 清空回收站
 */
export const emptyTrash = () =>
  request<void>({ url: '/nextwiki/trash/empty', method: 'DELETE' })
