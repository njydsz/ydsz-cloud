/**
 * @file 对外报价费率 Rate Card VO / DTO 类型定义
 * @description 定义对外报价费率模块的视图对象（VO）与数据传输对象（DTO），
 *              供 RateCardController 相关接口出入参使用。
 * @module api/resource/rate-card/types
 */

/** 对外报价费率视图对象 */
export interface RateCardVO {
  /** 费率记录ID */
  id?: string
  /** 费率编码 */
  rateCode: string
  /** 职级编码 */
  levelCode: string
  /** 项目类型 */
  projectType?: string
  /** 客户等级 */
  customerLevel?: string
  /** DAY / HOUR */
  billingUnit: string
  /** 费率金额 */
  rateAmount: number
  /** CNY / USD / EUR */
  currency?: string
  /** 生效日期 */
  effectiveDate: string
  /** 失效日期 */
  expiryDate?: string
  /** ACTIVE / INACTIVE */
  status?: string
  /** 备注 */
  remark?: string
}

/** 对外报价费率创建 DTO */
export interface RateCardCreateDTO {
  /** 费率编码 */
  rateCode: string
  /** 职级编码 */
  levelCode: string
  /** 项目类型 */
  projectType?: string
  /** 客户等级 */
  customerLevel?: string
  /** 计费单位：DAY / HOUR */
  billingUnit: string
  /** 费率金额 */
  rateAmount: number
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
