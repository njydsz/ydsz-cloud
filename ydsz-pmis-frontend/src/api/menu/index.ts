import { request } from '@/utils/request'

/**
 * 获取当前用户菜单树
 */
export const getMenuTreeApi = () =>
  request<unknown[]>({ url: '/menus/tree', method: 'GET' })
