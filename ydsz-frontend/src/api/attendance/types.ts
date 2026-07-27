/**
 * @file 考勤管理类型定义
 * @description 定义出勤记录、加班申请、请假申请的视图对象（VO）与入参对象（DTO），
 *              与后端 AttendanceController 返回结构对齐。
 * @module api/attendance/types
 */

/**
 * 出勤记录视图对象
 */
export interface AttendanceVO {
  /** 记录 ID */
  id: string
  /** 员工 ID */
  employeeId: number
  /** 员工姓名 */
  employeeName?: string
  /** 出勤日期（yyyy-MM-dd） */
  attendanceDate: string
  /** 上班打卡时间 */
  checkInTime?: string
  /** 下班打卡时间 */
  checkOutTime?: string
  /** 工时（小时） */
  workHours?: number
  /** 加班时长（小时） */
  overtimeHours?: number
  /** NORMAL/LATE/EARLY/ABSENT/LEAVE/OVERTIME */
  status: string
  /** WORKDAY/WEEKEND/HOLIDAY */
  workType?: string
  /** 备注 */
  remark?: string
}

/**
 * 出勤记录入参对象
 */
export interface AttendanceCreateDTO {
  /** 员工 ID */
  employeeId: number
  /** 员工姓名 */
  employeeName?: string
  /** 出勤日期（yyyy-MM-dd） */
  attendanceDate: string
  /** 上班打卡时间 */
  checkInTime?: string
  /** 下班打卡时间 */
  checkOutTime?: string
  /** 工时（小时） */
  workHours?: number
  /** 加班时长（小时） */
  overtimeHours?: number
  /** NORMAL/LATE/EARLY/ABSENT/LEAVE/OVERTIME */
  status?: string
  /** WORKDAY/WEEKEND/HOLIDAY */
  workType?: string
  /** 备注 */
  remark?: string
}

/**
 * 加班申请视图对象
 */
export interface OvertimeVO {
  /** 申请 ID */
  id: string
  /** 加班单号 */
  overtimeCode: string
  /** 员工 ID */
  employeeId: number
  /** 员工姓名 */
  employeeName?: string
  /** 加班日期（yyyy-MM-dd） */
  overtimeDate: string
  /** 开始时间 */
  startTime: string
  /** 结束时间 */
  endTime: string
  /** 加班时长（小时） */
  overtimeHours: number
  /** WORKDAY/WEEKEND/HOLIDAY */
  overtimeType: string
  /** 加班倍率 */
  payRate: number
  /** 加班事由 */
  reason?: string
  /** 审批状态（PENDING/APPROVED/REJECTED） */
  approvalStatus: string
  /** 审批人 ID */
  approverId?: number
  /** 审批人姓名 */
  approverName?: string
  /** 审批时间 */
  approvalTime?: string
  /** 审批备注 */
  approvalRemark?: string
}

/**
 * 加班申请入参对象
 */
export interface OvertimeCreateDTO {
  /** 员工 ID */
  employeeId: number
  /** 员工姓名 */
  employeeName?: string
  /** 加班日期（yyyy-MM-dd） */
  overtimeDate: string
  /** 开始时间 */
  startTime: string
  /** 结束时间 */
  endTime: string
  /** 加班时长（小时） */
  overtimeHours?: number
  /** WORKDAY/WEEKEND/HOLIDAY */
  overtimeType: string
  /** 加班倍率 */
  payRate?: number
  /** 加班事由 */
  reason?: string
}

/**
 * 请假申请视图对象
 */
export interface LeaveVO {
  /** 申请 ID */
  id: string
  /** 请假单号 */
  leaveCode: string
  /** 员工 ID */
  employeeId: number
  /** 员工姓名 */
  employeeName?: string
  /** ANNUAL/SICK/PERSONAL/MARRIAGE/MATERNITY/BEREAVEMENT/OTHER */
  leaveType: string
  /** 开始日期（yyyy-MM-dd） */
  startDate: string
  /** 结束日期（yyyy-MM-dd） */
  endDate: string
  /** 请假天数 */
  leaveDays: number
  /** 请假事由 */
  reason?: string
  /** 附件 URL */
  attachmentUrl?: string
  /** 审批状态（PENDING/APPROVED/REJECTED） */
  approvalStatus: string
  /** 审批人 ID */
  approverId?: number
  /** 审批人姓名 */
  approverName?: string
  /** 审批时间 */
  approvalTime?: string
  /** 审批备注 */
  approvalRemark?: string
}

/**
 * 请假申请入参对象
 */
export interface LeaveCreateDTO {
  /** 员工 ID */
  employeeId: number
  /** 员工姓名 */
  employeeName?: string
  /** ANNUAL/SICK/PERSONAL/MARRIAGE/MATERNITY/BEREAVEMENT/OTHER */
  leaveType: string
  /** 开始日期（yyyy-MM-dd） */
  startDate: string
  /** 结束日期（yyyy-MM-dd） */
  endDate: string
  /** 请假天数 */
  leaveDays?: number
  /** 请假事由 */
  reason?: string
  /** 附件 URL */
  attachmentUrl?: string
}
