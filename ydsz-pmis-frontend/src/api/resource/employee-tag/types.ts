export interface EmployeeTagVO {
  id: number
  employeeId: number
  /** SKILL/TECH/INDUSTRY/AVAILABILITY */
  tagType: string
  tagCode: string
  tagName: string
  weight?: number
  description?: string
}

export interface EmployeeTagCreateDTO {
  employeeId: number
  tagType: string
  tagCode: string
  tagName: string
  weight?: number
  description?: string
}
