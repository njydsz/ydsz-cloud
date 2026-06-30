export interface RateInternalVO {
  id?: number
  rateCode: string
  levelCode: string
  departmentId?: number
  departmentName?: string
  /** DAY / HOUR */
  billingUnit: string
  costAmount: number
  currency?: string
  effectiveDate: string
  expiryDate?: string
  status?: string
  remark?: string
}

export interface RateInternalCreateDTO {
  rateCode: string
  levelCode: string
  departmentId?: number
  departmentName?: string
  billingUnit: string
  costAmount: number
  currency?: string
  effectiveDate: string
  expiryDate?: string
  status?: string
  remark?: string
}
