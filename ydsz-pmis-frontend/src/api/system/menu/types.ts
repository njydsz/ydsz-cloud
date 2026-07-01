/**
 * @file 菜单与权限类型定义
 * @description 定义菜单/权限的树形视图对象、表单 DTO；
 *              与后端 PermissionController 出入参保持一致。
 * @module api/system/menu
 */

import type { BaseVO } from '@/types/api'

/**
 * 权限/菜单节点
 */
export interface MenuTreeVO extends BaseVO {
  /** 权限 ID */
  id: number
  /** 父权限 ID（顶级为 0） */
  parentId: number
  /** 权限编码 */
  permCode: string
  /** 权限名称 */
  permName: string
  /** 权限类型：MENU 菜单 / BUTTON 按钮 / API 接口 */
  permType: 'MENU' | 'BUTTON' | 'API'
  /** 前端路由路径 */
  path?: string
  /** 前端组件路径 */
  component?: string
  /** 图标 */
  icon?: string
  /** 排序号 */
  sortOrder?: number
  /** 是否可见：1 可见 / 0 隐藏 */
  visible?: number
  /** 子节点列表 */
  children?: MenuTreeVO[]
}

/**
 * 权限表单 DTO
 */
export interface PermissionFormDTO {
  /** 权限 ID（编辑时必填） */
  id?: number
  /** 父权限 ID（顶级为 0） */
  parentId?: number
  /** 权限编码 */
  permCode: string
  /** 权限名称 */
  permName: string
  /** 权限类型：MENU 菜单 / BUTTON 按钮 / API 接口 */
  permType: 'MENU' | 'BUTTON' | 'API'
  /** 前端路由路径 */
  path?: string
  /** 前端组件路径 */
  component?: string
  /** 图标 */
  icon?: string
  /** 排序号 */
  sortOrder?: number
  /** 是否可见：1 可见 / 0 隐藏 */
  visible?: number
  /** 状态：ENABLED/DISABLED */
  status?: string
}
