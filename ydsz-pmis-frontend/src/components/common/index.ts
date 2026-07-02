/**
 * @file 公共组件统一导出（barrel）
 * @description 汇总导出 P1/P2 前端体验优化新增的通用组件，便于业务页面按需引入.
 * @module components/common
 */
export { default as SkeletonTable } from './SkeletonTable.vue'
export { default as SkeletonCard } from './SkeletonCard.vue'
export { default as SkeletonDetail } from './SkeletonDetail.vue'
export { default as BatchToolbar } from './BatchToolbar.vue'
export type { BatchAction } from './BatchToolbar.vue'
export { default as VirtualTable } from './VirtualTable.vue'
export { default as PageLayout } from './PageLayout.vue'
// P2 前端体验优化: 可定制仪表盘 / 快速访问 / 行内编辑
export { default as CustomDashboard } from './CustomDashboard.vue'
export { default as QuickAccess } from './QuickAccess.vue'
export { default as InlineEdit } from './InlineEdit.vue'
// P1-8: 通用用户选择器（远程搜索 + 高级弹窗）
export { default as UserPicker } from './UserPicker.vue'
// P1-9: 意见编辑器（常用语 / @人 / 图片附件）
export { default as CommentEditor } from './CommentEditor.vue'
export type { CommentAttachment, CommentMention } from './CommentEditor.vue'
// P2-2: 嵌入式审批面板（业务页内嵌审批）
export { default as EmbeddedApprovalPanel } from './EmbeddedApprovalPanel.vue'
