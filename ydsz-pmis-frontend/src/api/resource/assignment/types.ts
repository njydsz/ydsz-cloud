export interface ResourceAssignmentVO {
  id: number
  employeeId: number
  employeeName?: string
  initiationId: number
  initiationName?: string
  /** RESERVE/START/TRANSFER/RELEASE/CANCEL */
  action: string
  /** ACTIVE/RELEASED/CANCELLED */
  status: string
  startDate?: string
  endDate?: string
  allocation?: number
  levelCode?: string
  remark?: string
}

export interface ResourceAssignmentCreateDTO {
  employeeId: number
  initiationId: number
  /** RESERVE/START/TRANSFER/RELEASE/CANCEL */
  action: string
  startDate?: string
  endDate?: string
  allocation?: number
  levelCode?: string
  remark?: string
}
