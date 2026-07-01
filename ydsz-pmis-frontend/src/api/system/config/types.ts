/**
 * @file 系统参数配置类型定义
 * @description 定义系统参数配置模块的视图对象、查询参数、表单 DTO；
 *              与后端 SysConfigController 出入参保持一致。
 * @module api/system/config
 */

import type { PageQuery } from '@/types/api'

/** 系统参数配置视图对象 */
export interface ConfigVO {
  /** 主键 ID */
  id: number
  /** 配置分组 */
  configGroup: string
  /** 配置键 */
  configKey: string
  /** 配置值 */
  configValue: string
  /** 默认值 */
  defaultValue?: string
  /** STRING/NUMBER/BOOLEAN/JSON */
  valueType: string
  /** 配置描述 */
  description?: string
  /** 1=前端可见, 0=私有 */
  isPublic: number
  /** 排序号 */
  sortOrder?: number
  /** 状态：ENABLED/DISABLED */
  status: string
  /** 创建人 ID */
  createdBy?: number
  /** 创建时间 */
  createdAt?: string
  /** 更新人 ID */
  updatedBy?: number
  /** 更新时间 */
  updatedAt?: string
}

/** 系统参数配置分页查询参数 */
export interface ConfigQuery extends PageQuery {
  /** 配置分组 */
  configGroup?: string
  /** 状态：ENABLED/DISABLED */
  status?: string
  /** 是否前端可见：1 可见 / 0 私有 */
  isPublic?: number
}

/** 系统参数配置表单 DTO（新增/编辑共用） */
export interface ConfigFormDTO {
  /** 主键 ID（编辑时必填） */
  id?: number
  /** 配置分组 */
  configGroup: string
  /** 配置键 */
  configKey: string
  /** 配置值 */
  configValue?: string
  /** 默认值 */
  defaultValue?: string
  /** 值类型：STRING/NUMBER/BOOLEAN/JSON */
  valueType?: string
  /** 配置描述 */
  description?: string
  /** 是否前端可见：1 可见 / 0 私有 */
  isPublic?: number
  /** 排序号 */
  sortOrder?: number
  /** 状态：ENABLED/DISABLED */
  status?: string
}
