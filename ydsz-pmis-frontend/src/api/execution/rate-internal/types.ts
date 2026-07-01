/**
 * @file 对内职级成本费率 VO / DTO 类型定义
 * @description 定义对内职级成本费率模块的视图对象（VO）与数据传输对象（DTO），
 *              供 RateInternalController 相关接口出入参使用。
 * @module api/execution/rate-internal/types
 */

/** 对内职级成本费率视图对象 */
export interface RateInternalVO {
  /** 费率记录ID */
  id?: number
  /** 费率编码 */
  rateCode: string
  /** 职级编码 */
  levelCode: string
  /** 部门ID */
  departmentId?: number
  /** 部门名称 */
  departmentName?: string
  /** DAY / HOUR */
  billingUnit: string
  /** 成本金额 */
  costAmount: number
  /** 币种：CNY / USD / EUR */
  currency?: string
  /** 生效日期 */
  effectiveDate: string
  /** 失效日期 */
  expiryDate?: string
  /** 状态：ACTIVE / INACTIVE */
  status?: string
  /** 备注 */
  remark?: string
}

/** 对内职级成本费率创建 DTO */
export interface RateInternalCreateDTO {
  /** 费率编码 */
  rateCode: string
  /** 职级编码 */
  levelCode: string
  /** 部门ID */
  departmentId?: number
  /** 部门名称 */
  departmentName?: string
  /** 计费单位：DAY / HOUR */
  billingUnit: string
  /** 成本金额 */
  costAmount: number
  /** 币种：CNY / USD / EUR */
  currency?: string
  /** 生效日期 */
  effectiveDate: string
  /** 失效日期 */
  expiryDate?: string
  /** 状态：ACTIVE / INACTIVE */
  status?: string
  /** 备注 */
  remark?: string
}
