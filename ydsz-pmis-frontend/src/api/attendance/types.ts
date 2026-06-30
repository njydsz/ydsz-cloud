export interface AttendanceVO {
  id: number
  employeeId: number
  employeeName?: string
  attendanceDate: string
  checkInTime?: string
  checkOutTime?: string
  workHours?: number
  overtimeHours?: number
  /** NORMAL/LATE/EARLY/ABSENT/LEAVE/OVERTIME */
  status: string
  /** WORKDAY/WEEKEND/HOLIDAY */
  workType?: string
  remark?: string
}

export interface AttendanceCreateDTO {
  employeeId: number
  employeeName?: string
  attendanceDate: string
  checkInTime?: string
  checkOutTime?: string
  workHours?: number
  overtimeHours?: number
  status?: string
  workType?: string
  remark?: string
}

export interface OvertimeVO {
  id: number
  overtimeCode: string
  employeeId: number
  employeeName?: string
  overtimeDate: string
  startTime: string
  endTime: string
  overtimeHours: number
  /** WORKDAY/WEEKEND/HOLIDAY */
  overtimeType: string
  payRate: number
  reason?: string
  approvalStatus: string
  approverId?: number
  approverName?: string
  approvalTime?: string
  approvalRemark?: string
}

export interface OvertimeCreateDTO {
  employeeId: number
  employeeName?: string
  overtimeDate: string
  startTime: string
  endTime: string
  overtimeHours?: number
  overtimeType: string
  payRate?: number
  reason?: string
}

export interface LeaveVO {
  id: number
  leaveCode: string
  employeeId: number
  employeeName?: string
  /** ANNUAL/SICK/PERSONAL/MARRIAGE/MATERNITY/BEREAVEMENT/OTHER */
  leaveType: string
  startDate: string
  endDate: string
  leaveDays: number
  reason?: string
  attachmentUrl?: string
  approvalStatus: string
  approverId?: number
  approverName?: string
  approvalTime?: string
  approvalRemark?: string
}

export interface LeaveCreateDTO {
  employeeId: number
  employeeName?: string
  leaveType: string
  startDate: string
  endDate: string
  leaveDays?: number
  reason?: string
  attachmentUrl?: string
}
