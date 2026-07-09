/**
 * @file 外包职级费率 类型定义
 * @description 定义外包职级费率（OutsourceRate, V1-V18）相关的 VO 类型。
 * @module api/resource/outsource-rate
 */

/** 外包职级费率 VO */
export interface OutsourceRateVO {
  /** 费率 ID */
  id: string
  /** 外包级别编码（V1-V18） */
  rateCode: string
  /** 级别名称 */
  rateName: string
  /** 级别段：PRIMARY / MIDDLE / SENIOR / EXPERT / STRATEGIC */
  levelSegment?: string
  /** 人天单价（元/天，外包核心计价单元） */
  dailyRate?: number
  /** 月工作天数（默认22天） */
  monthlyDays?: number
  /** 月度薪资（元/月, = dailyRate × monthlyDays） */
  monthlySalary?: number
  /** 差旅报销-公司承担部分（元/月） */
  travelReimbursement?: number
  /** 差旅补贴-公司承担部分（元/月） */
  travelAllowance?: number
  /** 公司总人力成本（元/月） */
  totalCost?: number
  /** 对外人天单价（元/天） */
  externalDaily?: number
  /** 对内人天成本（元/天） */
  internalDaily?: number
  /** 可计费利用率目标 */
  billableTarget?: number
  /** 排序序号 */
  sortOrder?: number
  /** 生效日期 */
  effectiveDate?: string
  /** 失效日期 */
  expireDate?: string
  /** 版本号 */
  version?: number
  /** 描述 */
  description?: string
  /** 状态 */
  status?: string
}

/** 外包职级费率创建 DTO */
export interface OutsourceRateCreateDTO {
  rateCode: string
  rateName: string
  levelSegment: string
  /** 人天单价（元/天，外包核心计价单元） */
  dailyRate: number
  /** 月工作天数（默认22天） */
  monthlyDays?: number
  travelReimbursement?: number
  travelAllowance?: number
  externalDaily?: number
  internalDaily?: number
  billableTarget?: number
  sortOrder?: number
  effectiveDate: string
  expireDate?: string
  version?: number
  description?: string
  status?: string
}

/** 外包职级费率更新 DTO（部分更新） */
export interface OutsourceRateUpdateDTO {
  rateCode?: string
  rateName?: string
  levelSegment?: string
  /** 人天单价（元/天，外包核心计价单元） */
  dailyRate?: number
  /** 月工作天数（默认22天） */
  monthlyDays?: number
  monthlySalary?: number
  travelReimbursement?: number
  travelAllowance?: number
  externalDaily?: number
  internalDaily?: number
  billableTarget?: number
  sortOrder?: number
  effectiveDate?: string
  expireDate?: string
  version?: number
  description?: string
  status?: string
}
