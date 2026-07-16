/**
 * @file 部门管理类型定义
 * @description 定义部门模块的视图对象、表单 DTO；
 *              与后端 DepartmentController 出入参保持一致。
 * @module api/system/dept
 */

import type { BaseVO } from '@/types/api'

/** 部门视图对象 */
export interface DeptVO extends BaseVO {
  /** 部门 ID */
  id: number
  /** 父部门 ID（顶级部门为 0） */
  parentId: number
  /** 部门编码 */
  deptCode: string
  /** 部门名称 */
  deptName: string
  /** 部门层级路径（如 /0/1/3） */
  deptPath: string
  /** 排序号 */
  sortOrder?: number
  /** 部门负责人 ID */
  leaderId?: number
  /** 部门负责人姓名 */
  leaderName?: string
  /** 联系电话 */
  phone?: string
  /** 邮箱 */
  email?: string
  /** 状态：ENABLED/DISABLED */
  status: string
  /** 子部门列表（树形结构时使用） */
  children?: DeptVO[]
}

/** 部门表单 DTO（新增/编辑共用） */
export interface DeptFormDTO {
  /** 部门 ID（编辑时必填） */
  id?: number
  /** 父部门 ID（顶级部门为 0） */
  parentId: number
  /** 部门编码 */
  deptCode: string
  /** 部门名称 */
  deptName: string
  /** 排序号 */
  sortOrder?: number
  /** 部门负责人 ID */
  leaderId?: number
  /** 联系电话 */
  phone?: string
  /** 邮箱 */
  email?: string
  /** 状态：ENABLED/DISABLED */
  status?: string
}
