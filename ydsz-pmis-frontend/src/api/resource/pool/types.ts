export interface ResourcePoolVO {
  id: number
  poolCode: string
  poolName: string
  /** HEADQUARTER/DIVISION/BACKUP */
  poolType: string
  levelRange?: string
  departmentId?: number
  departmentName?: string
  capacity?: number
  occupiedCount?: number
  managerId?: number
  managerName?: string
  description?: string
  status: string
}

export interface ResourcePoolCreateDTO {
  poolCode: string
  poolName: string
  poolType: string
  levelRange?: string
  departmentId?: number
  capacity?: number
  managerId?: number
  description?: string
  status?: string
}
