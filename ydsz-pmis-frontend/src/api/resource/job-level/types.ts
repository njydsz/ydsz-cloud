/**
 * @file 职级 类型定义
 * @description 定义职级（JobLevel）及其费率（JobLevelRate）相关的 VO 类型，供 job-level/index.ts 及上层业务使用。
 * @module api/resource/job-level
 */
export interface JobLevelVO {
  /** 职级 ID */
  id: number
  /** 职级编码（L1-L18） */
  levelCode: string
  /** 职级名称 */
  levelName: string
  /** 职级段：PRIMARY / MIDDLE / SENIOR / EXPERT / STRATEGIC */
  levelSegment?: string
  /** 排序序号 */
  sortOrder?: number
  /** 描述 */
  description?: string
  /** 状态 */
  status: string
}

export interface JobLevelRateVO {
  /** 费率 ID */
  id: number
  /** 职级编码 */
  levelCode: string
  /** 外部人天单价 */
  externalDaily?: number
  /** 内部人天单价 */
  internalDaily?: number
  /** 基本工资 */
  baseSalary?: number
  /** 社保-公司部分 */
  socialCompany?: number
  /** 社保-个人部分 */
  socialPersonal?: number
  /** 公积金-公司部分 */
  fundCompany?: number
  /** 公积金-个人部分 */
  fundPersonal?: number
  /** 税后到手 */
  takeHome?: number
  /** 总成本 */
  totalCost?: number
  /** 结算目标价 */
  billableTarget?: number
  /** 生效日期 */
  effectiveDate?: string
  /** 失效日期 */
  expireDate?: string
  /** 版本号 */
  version?: number
  /** 描述 */
  description?: string
}
