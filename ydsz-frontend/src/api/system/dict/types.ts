/**
 * @file 字典管理类型定义
 * @description 定义字典类型、字典项的视图对象、表单 DTO 及查询参数；
 *              与后端 DictController 出入参保持一致。
 * @module api/system/dict
 */

import type { BaseVO, PageQuery } from '@/types/api'

/** 字典类型视图对象 */
export interface DictTypeVO extends BaseVO {
  /** 字典类型编码 */
  typeCode: string
  /** 字典类型名称 */
  typeName: string
  /** 描述 */
  description?: string
  /** 字典项数量 */
  itemCount?: number
}

/** 字典项视图对象 */
export interface DictItemVO extends BaseVO {
  /** 所属字典类型编码 */
  typeCode: string
  /** 字典项编码 */
  itemCode: string
  /** 字典项值 */
  itemValue: string
  /** 排序号 */
  sortOrder?: number
  /** 状态：ENABLED/DISABLED */
  status: string
}

/** 字典类型表单 DTO（新增/编辑共用） */
export interface DictTypeFormDTO {
  /** 字典类型编码 */
  typeCode: string
  /** 字典类型名称 */
  typeName: string
  /** 描述 */
  description?: string
}

/** 字典项表单 DTO（新增/编辑共用） */
export interface DictItemFormDTO {
  /** 字典项 ID（编辑时必填） */
  id?: string
  /** 所属字典类型编码 */
  typeCode: string
  /** 字典项编码 */
  itemCode: string
  /** 字典项值 */
  itemValue: string
  /** 排序号 */
  sortOrder?: number
  /** 状态：ENABLED/DISABLED */
  status?: string
}

/** 字典项分页查询参数 */
export interface DictItemQuery extends PageQuery {
  /** 所属字典类型编码 */
  typeCode: string
  /** 关键字（模糊匹配 itemCode/itemValue） */
  keyword?: string
}
