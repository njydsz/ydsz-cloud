/**
 * @file 资源分配 类型定义
 * @description 定义资源分配（ResourceAssignment）相关的 VO 与 DTO 类型，供 assignment/index.ts 及上层业务使用。
 * @module api/resource/assignment
 */
export interface ResourceAssignmentVO {
  /** 分配记录 ID */
  id: string
  /** 员工 ID */
  employeeId: number
  /** 员工姓名 */
  employeeName?: string
  /** 立项 ID */
  initiationId: number
  /** 立项名称 */
  initiationName?: string
  /** 分配动作：RESERVE/START/TRANSFER/RELEASE/CANCEL */
  action: string
  /** 状态：ACTIVE/RELEASED/CANCELLED */
  status: string
  /** 生效日期 */
  startDate?: string
  /** 结束日期 */
  endDate?: string
  /** 分配比例（0-100） */
  allocation?: number
  /** 职级编码（如 L1-L18） */
  levelCode?: string
  /** 备注 */
  remark?: string
}

export interface ResourceAssignmentCreateDTO {
  /** 员工 ID */
  employeeId: number
  /** 立项 ID */
  initiationId: number
  /** 分配动作：RESERVE/START/TRANSFER/RELEASE/CANCEL */
  action: string
  /** 生效日期 */
  startDate?: string
  /** 结束日期 */
  endDate?: string
  /** 分配比例（0-100） */
  allocation?: number
  /** 职级编码（如 L1-L18） */
  levelCode?: string
  /** 备注 */
  remark?: string
}
