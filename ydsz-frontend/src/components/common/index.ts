/**
 * @fileoverview 公共组件统一导出（barrel）
 * @description 汇总导出 P1/P2 前端体验优化新增的通用组件，便于业务页面按需引入。
 * @module components/common
 * @author ydsz-team
 * @since 1.0.0
 */
export { default as ProTable } from './ProTable.vue'
export type { ProTableColumn } from './ProTable.vue'
export { default as SkeletonTable } from './SkeletonTable.vue'
export { default as SkeletonCard } from './SkeletonCard.vue'

export { default as BatchToolbar } from './BatchToolbar.vue'
export type { BatchAction } from './BatchToolbar.vue'
export { default as VirtualTable } from './VirtualTable.vue'
// P2-11: 通用虚拟滚动列表组件（非表格场景的大数据量列表）
export { default as VirtualList } from './VirtualList.vue'
export { default as PageLayout } from './PageLayout.vue'
// P2-1: 全局错误边界（基于 onErrorCaptured 捕获子组件树渲染异常）
export { default as ErrorBoundary } from './ErrorBoundary.vue'

// P1-8: 通用用户选择器（远程搜索 + 高级弹窗）
export { default as UserPicker } from './UserPicker.vue'
export type { UserModel } from './UserPicker.vue'
// P1-9: 意见编辑器（常用语 / @人 / 图片附件）
export { default as CommentEditor } from './CommentEditor.vue'
export type { CommentAttachment, CommentMention } from './CommentEditor.vue'

// 网盘知识库通用组件
export { default as FileUploader } from './FileUploader.vue'

export { default as FilePreviewer } from './FilePreviewer.vue'
export { default as QuotaProgressBar } from './QuotaProgressBar.vue'
export { default as ShareLinkDialog } from './ShareLinkDialog.vue'
