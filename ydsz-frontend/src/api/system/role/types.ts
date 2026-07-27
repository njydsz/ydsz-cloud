/**
 * @file 角色管理类型定义
 * @description 定义角色模块的视图对象、查询参数、表单 DTO；
 *              与后端 RoleController 出入参保持一致。
 * @module api/system/role
 */

import type { BaseVO, PageQuery } from '@/types/api'

/** 角色视图对象 */
export interface RoleVO extends BaseVO {
  /** 角色编码 */
  roleCode: string
  /** 角色名称 */
  roleName: string
  /** 描述 */
  description?: string
  /** 数据权限范围：ALL 全部 / DEPT 本部门 / SELF 本人 / CUSTOM 自定义 */
  dataScope: 'ALL' | 'DEPT' | 'SELF' | 'CUSTOM' | string
  /** 排序号 */
  sortOrder?: number
  /** 状态：ENABLED/DISABLED */
  status: string
  /** 已关联权限 ID 列表 */
  permissionIds?: number[]
}

/** 角色分页查询参数 */
export interface RoleQuery extends PageQuery {
  /** 关键字（模糊匹配 roleCode/roleName） */
  keyword?: string
  /** 状态：ENABLED/DISABLED */
  status?: string
}

/** 角色表单 DTO（新增/编辑共用） */
export interface RoleFormDTO {
  /** 角色 ID（编辑时必填） */
  id?: string
  /** 角色编码 */
  roleCode: string
  /** 角色名称 */
  roleName: string
  /** 描述 */
  description?: string
  /** 数据权限范围：ALL/DEPT/SELF/CUSTOM */
  dataScope: string
  /** 排序号 */
  sortOrder?: number
  /** 状态：ENABLED/DISABLED */
  status?: string
  /** 关联权限 ID 列表（全量覆盖） */
  permissionIds?: number[]
}
