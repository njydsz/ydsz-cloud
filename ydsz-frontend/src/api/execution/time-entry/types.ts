/**
 * @file 工时填报管理类型定义
 * @description 定义工时填报的视图对象、创建 DTO 及审批 DTO 等数据结构，
 *              供 api/execution/time-entry 模块及业务页面使用。
 * @module api/execution/time-entry/types
 */

export interface TimeEntryVO {
  /** 工时记录 ID */
  id: number
  /** 填报日期（YYYY-MM-DD） */
  entryDate: string
  /** 员工 ID */
  employeeId: number
  /** 员工姓名 */
  employeeName?: string
  /** 员工级别编码（用于成本核算） */
  levelCode?: string
  /** 所属立项 ID */
  initiationId: number
  /** 所属立项名称 */
  initiationName?: string
  /** 关联任务 ID */
  taskId?: number
  /** 关联任务名称 */
  taskName?: string
  /** 工时小时数 */
  hours: number
  /** 折算天数 */
  days?: number
  /** 加班小时数 */
  overtime?: number
  /** 工作类型：REGULAR/OVERTIME/TRAINING/LEAVE */
  workType?: string
  /** 备注说明 */
  description?: string
  /** 命中的费率卡 ID（由后端自动匹配，可空） */
  rateId?: number
  /** 人天费率（由后端自动匹配填入，用于成本归集） */
  rate?: number
  /** 状态：DRAFT/SUBMITTED/APPROVED/REJECTED */
  status?: string
  /** 审批人 ID */
  approverId?: number
  /** 审批人姓名 */
  approverName?: string
  /** 审批时间 */
  approvedAt?: string
  /** 驳回原因 */
  rejectReason?: string
  /** 创建时间 */
  createdAt?: string
}

export interface TimeEntryCreateDTO {
  /** 填报日期 */
  entryDate: string
  /** 员工 ID */
  employeeId: number
  /** 员工级别编码 */
  levelCode?: string
  /** 所属立项 ID */
  initiationId: number
  /** 关联任务 ID */
  taskId?: number
  /** 工时小时数 */
  hours: number
  /** 加班小时数 */
  overtime?: number
  /** 工作类型 */
  workType?: string
  /** 备注说明 */
  description?: string
  /** 费率卡 ID（可选，前端不传由后端自动匹配） */
  rateId?: number
  /** 人天费率（可选，前端只读展示，由后端自动匹配填入） */
  rate?: number
}

export interface TimeEntryApprovalDTO {
  /** 工时记录 ID */
  id: number
  /** 审批人 ID */
  approverId: number
  /** 审批人姓名 */
  approverName?: string
  /** 审批/驳回原因 */
  reason?: string
}
