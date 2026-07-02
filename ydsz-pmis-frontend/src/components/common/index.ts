/**
 * @file 公共组件统一导出（barrel）
 * @description 汇总导出 P1 前端体验优化新增的通用组件，便于业务页面按需引入.
 * @module components/common
 */
export { default as SkeletonTable } from './SkeletonTable.vue'
export { default as SkeletonCard } from './SkeletonCard.vue'
export { default as SkeletonDetail } from './SkeletonDetail.vue'
export { default as BatchToolbar } from './BatchToolbar.vue'
export type { BatchAction } from './BatchToolbar.vue'
export { default as VirtualTable } from './VirtualTable.vue'
export { default as PageLayout } from './PageLayout.vue'
