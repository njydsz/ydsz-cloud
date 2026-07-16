/**
 * @file 员工标签 类型定义
 * @description 定义员工标签（EmployeeTag）相关的 VO 与 DTO 类型，供 employee-tag/index.ts 及上层业务使用。
 * @module api/resource/employee-tag
 */
export interface EmployeeTagVO {
  /** 标签 ID */
  id: number
  /** 员工 ID */
  employeeId: number
  /** 标签类型：SKILL / TECH / INDUSTRY / AVAILABILITY */
  tagType: string
  /** 标签编码 */
  tagCode: string
  /** 标签名称 */
  tagName: string
  /** 权重 */
  weight?: number
  /** 描述 */
  description?: string
}

export interface EmployeeTagCreateDTO {
  /** 员工 ID */
  employeeId: number
  /** 标签类型：SKILL / TECH / INDUSTRY / AVAILABILITY */
  tagType: string
  /** 标签编码 */
  tagCode: string
  /** 标签名称 */
  tagName: string
  /** 权重 */
  weight?: number
  /** 描述 */
  description?: string
}
