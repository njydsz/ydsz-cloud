/**
 * @fileoverview 业务枚举常量集中定义
 * @description 将散落在 30+ 页面的 statusMap / typeMap / levelMap 集中管理：
 * - 按模块分组的 OptionVO 枚举（通用状态、项目模块、执行模块、财务模块、售后模块、资源模块）
 * - STATUS_TAG_TYPE: 状态到 el-tag type 的映射
 * - 工具函数: toOptions / getLabel
 * - 新代码应优先使用本文件常量，旧代码逐步迁移
 * @module constants/businessEnums
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import type { OptionVO } from '@/types/api'

// ==================== 通用状态枚举 ====================

/** 通用审批状态（DRAFT/SUBMITTED/APPROVED/REJECTED/ARCHIVED） */
export const APPROVAL_STATUS: Record<string, OptionVO> = {
  DRAFT: { label: '草稿', value: 'DRAFT' },
  SUBMITTED: { label: '已提交', value: 'SUBMITTED' },
  APPROVED: { label: '已审批', value: 'APPROVED' },
  REJECTED: { label: '已驳回', value: 'REJECTED' },
  ARCHIVED: { label: '已归档', value: 'ARCHIVED' },
}

/** 通用执行状态（PENDING/IN_PROGRESS/COMPLETED/CANCELLED） */
export const EXECUTION_STATUS: Record<string, OptionVO> = {
  PENDING: { label: '待处理', value: 'PENDING' },
  IN_PROGRESS: { label: '进行中', value: 'IN_PROGRESS' },
  COMPLETED: { label: '已完成', value: 'COMPLETED' },
  CANCELLED: { label: '已取消', value: 'CANCELLED' },
}

/** 发送状态（PENDING/SENT/FAILED/CANCELLED） */
export const SEND_STATUS: Record<string, OptionVO> = {
  PENDING: { label: '待发送', value: 'PENDING' },
  SENT: { label: '已发送', value: 'SENT' },
  FAILED: { label: '发送失败', value: 'FAILED' },
  CANCELLED: { label: '已取消', value: 'CANCELLED' },
}

// ==================== 项目模块 ====================

/** 项目阶段（INITIATION/PLANNING/EXECUTION/CLOSURE/ARCHIVED） */
export const PROJECT_STAGE: Record<string, OptionVO> = {
  INITIATION: { label: '立项', value: 'INITIATION' },
  PLANNING: { label: '规划', value: 'PLANNING' },
  EXECUTION: { label: '执行', value: 'EXECUTION' },
  CLOSURE: { label: '结项', value: 'CLOSURE' },
  ARCHIVED: { label: '归档', value: 'ARCHIVED' },
}

/** 商机等级（A/B/C/D） */
export const OPPORTUNITY_LEVEL: Record<string, OptionVO> = {
  A: { label: 'A级', value: 'A' },
  B: { label: 'B级', value: 'B' },
  C: { label: 'C级', value: 'C' },
  D: { label: 'D级', value: 'D' },
}

/** 信用等级（A/B/C/D） */
export const CREDIT_LEVEL: Record<string, OptionVO> = {
  A: { label: 'A级（90-100）', value: 'A' },
  B: { label: 'B级（75-89）', value: 'B' },
  C: { label: 'C级（60-74）', value: 'C' },
  D: { label: 'D级（0-59）', value: 'D' },
}

/** 阶段门状态（PASS/PENDING/BLOCKED） */
export const GATE_STATUS: Record<string, OptionVO> = {
  PASS: { label: '已通过', value: 'PASS' },
  PENDING: { label: '待评审', value: 'PENDING' },
  BLOCKED: { label: '已阻塞', value: 'BLOCKED' },
}

// ==================== 执行模块 ====================

/** WBS 任务状态 */
export const WBS_TASK_STATUS: Record<string, OptionVO> = {
  PLANNED: { label: '已计划', value: 'PLANNED' },
  IN_PROGRESS: { label: '进行中', value: 'IN_PROGRESS' },
  BLOCKED: { label: '已阻塞', value: 'BLOCKED' },
  IN_REVIEW: { label: '评审中', value: 'IN_REVIEW' },
  COMPLETED: { label: '已完成', value: 'COMPLETED' },
  CANCELLED: { label: '已取消', value: 'CANCELLED' },
}

/** 优先级（LOW/NORMAL/HIGH/URGENT） */
export const PRIORITY_LEVEL: Record<string, OptionVO> = {
  LOW: { label: '低', value: 'LOW' },
  NORMAL: { label: '普通', value: 'NORMAL' },
  HIGH: { label: '高', value: 'HIGH' },
  URGENT: { label: '紧急', value: 'URGENT' },
}

/** 风险等级（LOW/MEDIUM/HIGH/CRITICAL） */
export const RISK_LEVEL: Record<string, OptionVO> = {
  LOW: { label: '低', value: 'LOW' },
  MEDIUM: { label: '中', value: 'MEDIUM' },
  HIGH: { label: '高', value: 'HIGH' },
  CRITICAL: { label: '严重', value: 'CRITICAL' },
}

/** 预警级别（NORMAL/YELLOW/RED） */
export const ALERT_LEVEL: Record<string, OptionVO> = {
  NORMAL: { label: '正常', value: 'NORMAL' },
  YELLOW: { label: '黄色预警', value: 'YELLOW' },
  RED: { label: '红色预警', value: 'RED' },
}

/** 变更状态（DRAFT/SUBMITTED/UNDER_REVIEW/APPROVED/REJECTED/EXECUTING/CANCELLED） */
export const CHANGE_STATUS: Record<string, OptionVO> = {
  DRAFT: { label: '草稿', value: 'DRAFT' },
  SUBMITTED: { label: '已提交', value: 'SUBMITTED' },
  UNDER_REVIEW: { label: '评审中', value: 'UNDER_REVIEW' },
  APPROVED: { label: '已审批', value: 'APPROVED' },
  REJECTED: { label: '已驳回', value: 'REJECTED' },
  EXECUTING: { label: '执行中', value: 'EXECUTING' },
  CANCELLED: { label: '已取消', value: 'CANCELLED' },
}

// ==================== 财务模块 ====================

/** 合同状态 */
export const CONTRACT_STATUS: Record<string, OptionVO> = {
  DRAFT: { label: '草稿', value: 'DRAFT' },
  ACTIVE: { label: '生效', value: 'ACTIVE' },
  EXPIRED: { label: '已过期', value: 'EXPIRED' },
  TERMINATED: { label: '已终止', value: 'TERMINATED' },
}

/** 发票状态 */
export const INVOICE_STATUS: Record<string, OptionVO> = {
  DRAFT: { label: '草稿', value: 'DRAFT' },
  ISSUED: { label: '已开票', value: 'ISSUED' },
  RED_REVERSED: { label: '红冲', value: 'RED_REVERSED' },
  CANCELLED: { label: '已作废', value: 'CANCELLED' },
}

/** 付款状态 */
export const PAYMENT_STATUS: Record<string, OptionVO> = {
  PENDING: { label: '待收款', value: 'PENDING' },
  PARTIAL: { label: '部分收款', value: 'PARTIAL' },
  RECEIVED: { label: '已收款', value: 'RECEIVED' },
  CANCELLED: { label: '已取消', value: 'CANCELLED' },
}

/** 采购状态 */
export const PURCHASE_STATUS: Record<string, OptionVO> = {
  DRAFT: { label: '草稿', value: 'DRAFT' },
  SUBMITTED: { label: '已提交', value: 'SUBMITTED' },
  APPROVED: { label: '已审批', value: 'APPROVED' },
  REJECTED: { label: '已驳回', value: 'REJECTED' },
  RECEIVED: { label: '已入库', value: 'RECEIVED' },
}

// ==================== 售后模块 ====================

/** 工单状态 */
export const OPS_TICKET_STATUS: Record<string, OptionVO> = {
  OPEN: { label: '待处理', value: 'OPEN' },
  IN_PROGRESS: { label: '处理中', value: 'IN_PROGRESS' },
  RESOLVED: { label: '已解决', value: 'RESOLVED' },
  CLOSED: { label: '已关闭', value: 'CLOSED' },
  REOPENED: { label: '已重开', value: 'REOPENED' },
}

/** 满意度等级 */
export const SATISFACTION_LEVEL: Record<string, OptionVO> = {
  VERY_SATISFIED: { label: '非常满意', value: 'VERY_SATISFIED' },
  SATISFIED: { label: '满意', value: 'SATISFIED' },
  NEUTRAL: { label: '一般', value: 'NEUTRAL' },
  DISSATISFIED: { label: '不满意', value: 'DISSATISFIED' },
  VERY_DISSATISFIED: { label: '非常不满意', value: 'VERY_DISSATISFIED' },
}

// ==================== 资源模块 ====================

/** 资源分配状态 */
export const ASSIGNMENT_STATUS: Record<string, OptionVO> = {
  RESERVED: { label: '已预留', value: 'RESERVED' },
  ACTIVE: { label: '已生效', value: 'ACTIVE' },
  TRANSFERRED: { label: '已转移', value: 'TRANSFERRED' },
  RELEASED: { label: '已释放', value: 'RELEASED' },
  CANCELLED: { label: '已取消', value: 'CANCELLED' },
}

// ==================== 辅助工具 ====================

/** StatusTag 组件所用的 tag 类型映射（el-tag type） */
export const STATUS_TAG_TYPE: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
  SUCCESS: 'success',
  COMPLETED: 'success',
  APPROVED: 'success',
  ACTIVE: 'success',
  RECEIVED: 'success',
  RESOLVED: 'success',
  PASS: 'success',

  PENDING: 'warning',
  SUBMITTED: 'warning',
  IN_PROGRESS: 'primary',
  IN_REVIEW: 'primary',
  UNDER_REVIEW: 'primary',
  PARTIAL: 'warning',
  RESERVED: 'warning',
  OPEN: 'warning',

  DRAFT: 'info',
  ARCHIVED: 'info',
  CANCELLED: 'info',
  EXPIRED: 'info',

  BLOCKED: 'danger',
  REJECTED: 'danger',
  FAILED: 'danger',
  TERMINATED: 'danger',
  RED_REVERSED: 'danger',
  CRITICAL: 'danger',
  RED: 'danger',
  VERY_DISSATISFIED: 'danger',
}

/**
 * 从枚举 Map 中提取 OptionVO[] 数组（用于下拉选择器）
 * @param map - 枚举 Map（如 WBS_TASK_STATUS）
 * @returns OptionVO 数组
 */
export function toOptions(map: Record<string, OptionVO>): OptionVO[] {
  return Object.values(map)
}

/**
 * 根据 value 获取 label
 * @param map - 枚举 Map
 * @param value - 状态值
 * @param fallback - 未找到时的回退文案
 */
export function getLabel(
  map: Record<string, OptionVO>,
  value: string | undefined | null,
  fallback = '-',
): string {
  if (!value) return fallback
  const item = Object.values(map).find((v) => v.value === value)
  return item?.label ?? fallback
}
