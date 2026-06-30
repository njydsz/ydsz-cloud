export interface RateCardVO {
  id?: number
  rateCode: string
  levelCode: string
  projectType?: string
  customerLevel?: string
  /** DAY / HOUR */
  billingUnit: string
  rateAmount: number
  /** CNY / USD / EUR */
  currency?: string
  effectiveDate: string
  expiryDate?: string
  /** ACTIVE / INACTIVE */
  status?: string
  remark?: string
}

export interface RateCardCreateDTO {
  rateCode: string
  levelCode: string
  projectType?: string
  customerLevel?: string
  billingUnit: string
  rateAmount: number
  currency?: string
  effectiveDate: string
  expiryDate?: string
  status?: string
  remark?: string
}
