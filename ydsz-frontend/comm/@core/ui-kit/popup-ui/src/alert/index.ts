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
