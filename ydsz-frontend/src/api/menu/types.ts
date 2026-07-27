/**
 * @file 菜单与权限类型定义
 * @description 定义菜单/权限树节点结构，与后端 PermissionController 返回结构对齐。
 * @module api/menu/types
 */

/**
 * 菜单树节点
 */
export interface MenuTreeNode {
  /** 节点 ID */
  id: string
  /** 父节点 ID（根节点为 0 或 -1） */
  parentId: number
  /** 权限编码（角色绑定用） */
  permCode: string
  /** 权限/菜单名称 */
  permName: string
  /** 权限类型：MENU 菜单 / BUTTON 按钮 / API 接口 */
  permType: 'MENU' | 'BUTTON' | 'API' | string
  /** 前端路由 path（MENU 类型） */
  path?: string
  /** 前端组件路径（MENU 类型） */
  component?: string
  /** 菜单图标 */
  icon?: string
  /** 同级排序值，越小越靠前 */
  sortOrder?: number
  /** 是否可见：1 可见 / 0 隐藏 */
  visible?: number
  /** 子节点 */
  children?: MenuTreeNode[]
}
