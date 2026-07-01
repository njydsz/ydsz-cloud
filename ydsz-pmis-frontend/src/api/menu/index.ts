/**
 * @file 菜单与权限 API
 * @description 提供当前用户菜单树、全量权限树（管理端）以及当前用户权限码查询能力，
 *              对应后端 PermissionController（/permissions/**）。
 * @module api/menu
 */
import { request } from '@/utils/request'
import type { MenuTreeNode } from './types'

export * from './types'

/**
 * 拉取当前用户的菜单树
 *
 * 根据当前登录用户的角色返回可见的菜单/按钮/API 权限树，前端用于动态路由与按钮鉴权。
 *
 * @returns 菜单树节点数组
 */
export const getMenuTreeApi = () =>
  request<MenuTreeNode[]>({ url: '/permissions/menu-tree', method: 'GET' })

/**
 * 拉取所有权限(管理端)
 *
 * 返回系统中全量权限树，供权限分配/角色管理界面使用。
 *
 * @returns 全量权限树节点数组
 */
export const getAllPermissionsApi = () =>
  request<MenuTreeNode[]>({ url: '/permissions/tree', method: 'GET' })

/**
 * 拉取当前用户的权限编码
 *
 * 返回当前用户拥有的权限码字符串数组，用于前端按钮级鉴权（v-permission）。
 *
 * @returns 权限码数组
 */
export const getMyPermCodesApi = () =>
  request<string[]>({ url: '/permissions/mine', method: 'GET' })
