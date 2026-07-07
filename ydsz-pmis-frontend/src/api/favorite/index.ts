/**
 * @file 收藏与最近访问 API (P2-14 收藏/快速访问)
 * @description 提供收藏夹的增删查与最近访问记录能力, 对应后端
 *              FavoriteController(/favorites) 与 RecentAccessController(/recent-access).
 *               前缀由 VITE_API_PREFIX 注入, 此处 BASE 仅写 /favorites.
 * @module api/favorite
 */
import { request } from '@/utils/request'

/** 收藏接口路径( 前缀由 baseURL 注入) */
const BASE = '/favorites'

/** 收藏记录 */
export interface FavoriteVO {
  id: number
  userId: number
  /** 收藏对象类型: PAGE 页面 / PROJECT 项目 / CONTRACT 合同 */
  targetType: string
  targetId: string
  targetName: string
  targetPath?: string
  createdAt: string
}

/** 最近访问记录 */
export interface RecentAccessVO {
  id: number
  userId: number
  path: string
  title: string
  accessedAt: string
}

/**
 * 查询当前用户的收藏列表
 * @returns 收藏记录数组
 */
export const getFavorites = () =>
  request<FavoriteVO[]>({ url: BASE, method: 'GET', silent: true })

/**
 * 新增收藏
 * @param data 收藏参数
 * @returns 新建的收藏记录
 */
export const addFavorite = (data: Partial<FavoriteVO>) =>
  request<FavoriteVO>({ url: BASE, method: 'POST', data })

/**
 * 取消收藏
 * @param id 收藏记录 ID
 */
export const removeFavorite = (id: number) =>
  request<void>({ url: `${BASE}/${id}`, method: 'DELETE', silent: true })

/**
 * 查询当前用户最近访问记录
 * @returns 最近访问记录数组
 */
export const getRecentAccess = () =>
  request<RecentAccessVO[]>({ url: '/recent-access', method: 'GET', silent: true })

/**
 * 记录一次页面访问
 * @param path 路由路径
 * @param title 页面标题
 */
export const recordAccess = (path: string, title: string) =>
  request<void>({
    url: '/recent-access',
    method: 'POST',
    data: { path, title },
    silent: true,
  })
