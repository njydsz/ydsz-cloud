import { request } from '@/utils/request'
import type { MenuTreeNode } from './types'

/**
 * 拉取当前用户的菜单树
 */
export const getMenuTreeApi = () =>
  request<MenuTreeNode[]>({ url: '/permissions/menu-tree', method: 'GET' })

/**
 * 拉取所有权限(管理端)
 */
export const getAllPermissionsApi = () =>
  request<MenuTreeNode[]>({ url: '/permissions/tree', method: 'GET' })

/**
 * 拉取当前用户的权限编码
 */
export const getMyPermCodesApi = () =>
  request<string[]>({ url: '/permissions/mine', method: 'GET' })
