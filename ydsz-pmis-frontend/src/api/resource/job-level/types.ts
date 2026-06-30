export interface JobLevelVO {
  id: number
  levelCode: string
  levelName: string
  /** PRIMARY/MIDDLE/SENIOR/EXPERT/STRATEGIC */
  levelSegment?: string
  sortOrder?: number
  description?: string
  status: string
}

export interface JobLevelRateVO {
  id: number
  levelCode: string
  externalDaily?: number
  internalDaily?: number
  baseSalary?: number
  socialCompany?: number
  socialPersonal?: number
  fundCompany?: number
  fundPersonal?: number
  takeHome?: number
  totalCost?: number
  billableTarget?: number
  effectiveDate?: string
  expireDate?: string
  version?: number
  description?: string
}
