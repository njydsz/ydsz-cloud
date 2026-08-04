/**
 * @ydsz/shared-business — 跨子应用共享的业务组件包
 *
 * 消除各子应用中重复的业务 UI 组件，如：
 * - 状态徽章（项目阶段、任务状态、审批状态）
 * - 用户头像（含在线状态、角色标签）
 * - 文件预览图标
 * - 数据字典选择器
 *
 * 子应用按需引用即可，无需各自实现。
 */

// 状态徽章组件 — 统一的项目/任务/审批状态展示
export { default as StatusBadge } from './components/status-badge.vue';

// 用户头像组件 — 含在线状态、角色标签
export { default as UserAvatar } from './components/user-avatar.vue';

// 字典选择器组件 — 从全局字典缓存获取数据
export { default as DictSelect } from './components/dict-select.vue';

// 字典标签组件 — 字典值渲染为彩色标签（表格列常用）
export { default as DictTag } from './components/dict-tag.vue';

// 文件类型图标组件
export { default as FileIcon } from './components/file-icon.vue';

// Excel 导出按钮组件 — 声明式导出
export { default as ExcelExportButton } from './components/excel-export-button.vue';

// Excel 导入按钮组件 — 声明式导入
export { default as ExcelImportButton } from './components/excel-import-button.vue';

// Excel 导入导出 composable
export {
  useExcelExport,
  type ExcelExportColumn,
  type ExcelExportOptions,
} from './composables/use-excel-export';
export {
  useExcelImport,
  type ExcelImportColumn,
  type ExcelImportResult,
  type ExcelImportOptions,
} from './composables/use-excel-import';

// 统一空状态组件
export { default as EmptyState } from './components/empty-state.vue';

// 统一错误状态组件
export { default as ErrorState } from './components/error-state.vue';

// 异步状态容器组件（loading/error/empty/data 自动切换）
export { default as AsyncState } from './components/async-state.vue';

// 键盘快捷键帮助面板
export { default as KeyboardHelp } from './components/keyboard-help.vue';

// 键盘快捷键 composable
export {
  useKeyboardShortcut,
  bindGlobalShortcut,
  clearScope,
  type ShortcutDescriptor,
} from './composables/use-keyboard-shortcut';

// 实时通信（WebSocket）
export {
  RealtimeClient,
  useRealtime,
  getRealtimeClient,
  type RealtimeOptions,
  type RealtimeStatus,
} from './realtime';

// 审计日志表格组件
export { default as AuditLogTable } from './components/audit-log-table.vue';

// 审计日志查询 composable
export {
  useAuditLog,
  type AuditLogFetcher,
  type AuditLogItem,
  type AuditLogQuery,
  type AuditLogPageResult,
} from './composables/use-audit-log';

// 服务端分页 composable
export {
  useServerPagination,
  type ServerPaginationFetcher,
  type ServerPaginationOptions,
} from './composables/use-server-pagination';

// 通用 CRUD 列表 composable
export {
  useCrudTable,
  type CrudTableOptions,
  type DeleteFetcher,
} from './composables/use-crud-table';

// 大数据量下拉选择器
export { default as VirtualSelect } from './components/virtual-select.vue';

// 通用虚拟列表
export { default as VirtualList } from './components/virtual-list.vue';

// 用户操作引导组件
export { default as AppTour } from './components/app-tour.vue';

// 引导 composable
export {
  useAppTour,
  type AppTourOptions,
  type TourStep,
} from './composables/use-app-tour';

// 移动端响应式适配 composable
export {
  useResponsive,
  type ResponsiveColumn,
} from './composables/use-responsive';

// ===== 统一适配器（消除 9 个子应用的重复代码） =====
export {
  initSetupYDSZForm,
  useYDSZForm,
  z,
  type YDSZFormProps,
  type YDSZFormSchema,
} from './adapter/form';
export {
  initComponentAdapter,
  type ComponentType,
} from './adapter/component';
export { useYDSZVxeGrid } from './adapter/vxe-table';
