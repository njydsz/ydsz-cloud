/**
 * @file 用户管理类型定义
 * @description 定义用户模块的视图对象、查询参数、创建 DTO；
 *              与后端 UserController 出入参保持一致。
 * @module api/system/user
 */

import type { PageQuery, BaseVO } from '@/types/api'

/** 用户视图对象 */
export interface UserVO extends BaseVO {
  /** 登录账号 */
  username: string
  /** 真实姓名 */
  realName: string
  /** 邮箱 */
  email?: string
  /** 手机号 */
  phone?: string
  /** 职级编码 */
  levelCode?: string
  /** 职级名称 */
  levelName?: string
  /** 所属部门 ID */
  departmentId?: number
  /** 所属部门名称 */
  departmentName?: string
  /** 岗位 ID */
  positionId?: number
  /** 岗位名称 */
  positionName?: string
}

/** 用户分页查询参数 */
export interface UserQuery extends PageQuery {
  /** 所属部门 ID */
  departmentId?: number
  /** 职级编码 */
  levelCode?: string
  /** 状态：ENABLED/DISABLED */
  status?: string
}

/** 用户创建/更新 DTO */
export interface UserCreateDTO {
  /** 登录账号 */
  username: string
  /** 真实姓名 */
  realName: string
  /** 密码（创建时必填，更新时留空表示不修改） */
  password?: string
  /** 邮箱 */
  email?: string
  /** 手机号 */
  phone?: string
  /** 职级编码 */
  levelCode?: string
  /** 所属部门 ID */
  departmentId?: number
  /** 岗位 ID */
  positionId?: number
  /** 关联角色 ID 列表 */
  roleIds?: number[]
  /** 状态：ENABLED/DISABLED */
  status?: string
}
