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

// 字典选择器组件 — 从 system 模块获取字典数据
export { default as DictSelect } from './components/dict-select.vue';

// 文件类型图标组件
export { default as FileIcon } from './components/file-icon.vue';
