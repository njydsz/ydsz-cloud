/**
 * @file 员工 类型定义
 * @description 定义员工（Employee）相关的 VO / DTO 类型。
 * @module api/resource/employee
 */

/** 员工视图 VO */
export interface EmployeeVO {
  /** 员工 ID */
  id: string
  /** 用户 ID */
  userId: string
  /** 员工编码 */
  empCode: string
  /** 员工姓名 */
  empName: string
  /** 身份证号 */
  idCard?: string
  /** 性别 */
  gender?: string
  /** 出生日期 */
  birthDate?: string
  /** 手机号 */
  phone?: string
  /** 邮箱 */
  email?: string
  /** 部门 ID */
  departmentId: string
  /** 岗位 ID */
  positionId?: string
  /** 职级编码 */
  levelCode: string
  /** 员工类型 */
  employeeType?: string
  /** 兼职费率 ID */
  partTimeRateId?: string
  /** 外包费率 ID */
  outsourceRateId?: string
  /** 入职日期 */
  hireDate: string
  /** 离职日期 */
  leaveDate?: string
  /** 工作状态 */
  workStatus: string
  /** Bench 状态 */
  benchStatus?: string
  /** Bench 开始日期 */
  benchStart?: string
  /** 头像 URL */
  avatar?: string
  /** 地址 */
  address?: string
  /** 紧急联系人 */
  emergencyContact?: string
  /** 紧急联系电话 */
  emergencyPhone?: string
  /** 描述 */
  description?: string
  /** 装配字段 */
  departmentName?: string
  /** 岗位名称 */
  positionName?: string
  /** 职级名称 */
  levelName?: string
  /** 兼职费率名称 */
  partTimeRateName?: string
  /** 外包费率名称 */
  outsourceRateName?: string
}

/** 员工创建 DTO */
export interface EmployeeCreateDTO {
  /** 用户 ID */
  userId: string
  /** 员工编码 */
  empCode: string
  /** 员工姓名 */
  empName: string
  /** 身份证号 */
  idCard?: string
  /** 性别 */
  gender?: string
  /** 出生日期 */
  birthDate?: string
  /** 手机号 */
  phone?: string
  /** 邮箱 */
  email?: string
  /** 部门 ID */
  departmentId: string
  /** 岗位 ID */
  positionId?: string
  /** 职级编码 */
  levelCode: string
  /** 员工类型 */
  employeeType?: string
  /** 兼职费率 ID */
  partTimeRateId?: string
  /** 外包费率 ID */
  outsourceRateId?: string
  /** 入职日期 */
  hireDate: string
  /** 离职日期 */
  leaveDate?: string
  /** 工作状态 */
  workStatus?: string
  /** 头像 URL */
  avatar?: string
  /** 地址 */
  address?: string
  /** 紧急联系人 */
  emergencyContact?: string
  /** 紧急联系电话 */
  emergencyPhone?: string
  /** 描述 */
  description?: string
}

/** 员工更新 DTO */
export interface EmployeeUpdateDTO {
  /** 用户 ID */
  userId?: string
  /** 员工编码 */
  empCode?: string
  /** 员工姓名 */
  empName?: string
  /** 身份证号 */
  idCard?: string
  /** 性别 */
  gender?: string
  /** 出生日期 */
  birthDate?: string
  /** 手机号 */
  phone?: string
  /** 邮箱 */
  email?: string
  /** 部门 ID */
  departmentId?: string
  /** 岗位 ID */
  positionId?: string
  /** 职级编码 */
  levelCode?: string
  /** 员工类型 */
  employeeType?: string
  /** 兼职费率 ID */
  partTimeRateId?: string
  /** 外包费率 ID */
  outsourceRateId?: string
  /** 入职日期 */
  hireDate?: string
  /** 离职日期 */
  leaveDate?: string
  /** 工作状态 */
  workStatus?: string
  /** Bench 状态 */
  benchStatus?: string
  /** Bench 开始日期 */
  benchStart?: string
  /** 头像 URL */
  avatar?: string
  /** 地址 */
  address?: string
  /** 紧急联系人 */
  emergencyContact?: string
  /** 紧急联系电话 */
  emergencyPhone?: string
  /** 描述 */
  description?: string
}
