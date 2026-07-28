/**
 * index 模块
 *
 * @path comm\@core\ui-kit\popup-ui\src\alert\index.ts
 * @author ydsz-team
 * @since 1.0.0
 */
export type {
  AlertProps,
  BeforeCloseScope,
  IconType,
  PromptProps,
} from './alert';
export { useAlertContext } from './alert';
export { default as Alert } from './alert.vue';
export {
  ydszAlert as alert,
  clearAllAlerts,
  ydszConfirm as confirm,
  ydszPrompt as prompt,
} from './AlertBuilder';
