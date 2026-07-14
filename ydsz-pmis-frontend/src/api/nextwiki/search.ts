/**
 * @file 搜索 API
 * @description 提供全文搜索与索引重建能力，对应后端 SearchController（/nextwiki/search）。
 * @module api/nextwiki/search
 */
import { request } from '@/utils/request'
import type { SearchResultVO } from './types'

/**
 * 搜索文件（按文件名、内容全文检索）
 * @param keyword 搜索关键字
 * @param page 当前页码
 * @param size 每页条数
 * @returns 搜索结果列表
 */
export const searchFiles = (keyword: string, page?: number, size?: number) =>
  request<{ list: SearchResultVO[]; total: number }>({
    url: '/nextwiki/search',
    method: 'GET',
    params: { keyword, page, size },
  })

/**
 * 重建搜索索引（管理员操作）
 */
export const rebuildSearchIndex = () =>
  request<void>({ url: '/nextwiki/search/rebuild', method: 'POST' })
