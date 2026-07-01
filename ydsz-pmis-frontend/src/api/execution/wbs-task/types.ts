/**
 * @file WBS 任务管理类型定义
 * @description 定义 WBS 任务的视图对象、创建 DTO 及状态变更 DTO 等数据结构，
 *              供 api/execution/wbs-task 模块及业务页面使用。
 * @module api/execution/wbs-task/types
 */

export interface WbsTaskVO {
  /** 任务 ID */
  id: number
  /** 任务编码（唯一） */
  taskCode: string
  /** 任务名称 */
  taskName: string
  /** 所属立项 ID */
  initiationId: number
  /** 所属立项名称 */
  initiationName?: string
  /** 父任务 ID（用于构建 WBS 层级树） */
  parentId?: number
  /** 任务层级（1 为根节点，逐级递增） */
  taskLevel?: number
  /** WBS 完整路径（如 1.2.3） */
  wbsPath?: string
  /** 同级排序号 */
  sortOrder?: number
  /** 任务类型：TASK/MILESTONE/SUMMARY */
  taskType?: string
  /** 计划开始日期 */
  plannedStartDate?: string
  /** 计划结束日期 */
  plannedEndDate?: string
  /** 实际开始日期 */
  actualStartDate?: string
  /** 实际结束日期 */
  actualEndDate?: string
  /** 计划工期（天） */
  durationDays?: number
  /** 计划工时（人时） */
  plannedEffort?: number
  /** 实际工时（人时） */
  actualEffort?: number
  /** 完成进度百分比（0-100） */
  progressPct?: number
  /** 责任人 ID */
  ownerId: number
  /** 责任人姓名 */
  ownerName?: string
  /** 参与人 ID 列表（逗号分隔） */
  assigneeIds?: string
  /** 优先级：LOW/NORMAL/HIGH/URGENT */
  priority?: string
  /** 状态：PLANNED/IN_PROGRESS/BLOCKED/IN_REVIEW/COMPLETED/CANCELLED */
  status?: string
  /** 前置依赖任务 ID 列表（逗号分隔） */
  dependsOn?: string
  /** 是否里程碑（0/1） */
  milestone?: number
  /** 任务描述 */
  description?: string
  /** 交付物说明 */
  deliverable?: string
  /** 风险等级：LOW/MEDIUM/HIGH */
  riskLevel?: string
}

export interface WbsTaskCreateDTO {
  /** 任务编码 */
  taskCode: string
  /** 任务名称 */
  taskName: string
  /** 所属立项 ID */
  initiationId: number
  /** 父任务 ID */
  parentId?: number
  /** 任务层级 */
  taskLevel?: number
  /** 任务类型 */
  taskType?: string
  /** 计划开始日期 */
  plannedStartDate?: string
  /** 计划结束日期 */
  plannedEndDate?: string
  /** 计划工时 */
  plannedEffort?: number
  /** 责任人 ID */
  ownerId: number
  /** 参与人 ID 列表（逗号分隔） */
  assigneeIds?: string
  /** 优先级 */
  priority?: string
  /** 任务描述 */
  description?: string
  /** 交付物说明 */
  deliverable?: string
  /** 前置依赖任务 ID 列表 */
  dependsOn?: string
}

export interface WbsTaskStatusDTO {
  /** 任务 ID */
  id: number
  /** 目标状态 */
  targetStatus: string
  /** 进度百分比 */
  progressPct?: number
  /** 状态变更原因/备注 */
  reason?: string
}
