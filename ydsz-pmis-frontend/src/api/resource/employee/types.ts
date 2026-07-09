/**
 * @file 员工 类型定义
 * @description 定义员工（Employee）相关的 VO / DTO 类型。
 * @module api/resource/employee
 */

/** 员工视图 VO */
export interface EmployeeVO {
  id: string
  userId: string
  empCode: string
  empName: string
  idCard?: string
  gender?: string
  birthDate?: string
  phone?: string
  email?: string
  departmentId: string
  positionId?: string
  levelCode: string
  employeeType?: string
  partTimeRateId?: string
  outsourceRateId?: string
  hireDate: string
  leaveDate?: string
  workStatus: string
  benchStatus?: string
  benchStart?: string
  avatar?: string
  address?: string
  emergencyContact?: string
  emergencyPhone?: string
  description?: string
  /** 装配字段 */
  departmentName?: string
  positionName?: string
  levelName?: string
  partTimeRateName?: string
  outsourceRateName?: string
}

/** 员工创建 DTO */
export interface EmployeeCreateDTO {
  userId: string
  empCode: string
  empName: string
  idCard?: string
  gender?: string
  birthDate?: string
  phone?: string
  email?: string
  departmentId: string
  positionId?: string
  levelCode: string
  employeeType?: string
  partTimeRateId?: string
  outsourceRateId?: string
  hireDate: string
  workStatus?: string
  avatar?: string
  address?: string
  emergencyContact?: string
  emergencyPhone?: string
  description?: string
}

/** 员工更新 DTO */
export interface EmployeeUpdateDTO {
  userId?: string
  empCode?: string
  empName?: string
  idCard?: string
  gender?: string
  birthDate?: string
  phone?: string
  email?: string
  departmentId?: string
  positionId?: string
  levelCode?: string
  employeeType?: string
  partTimeRateId?: string
  outsourceRateId?: string
  hireDate?: string
  leaveDate?: string
  workStatus?: string
  benchStatus?: string
  benchStart?: string
  avatar?: string
  address?: string
  emergencyContact?: string
  emergencyPhone?: string
  description?: string
}
