/**
 * 国际化配置入口
 *
 * @path comm\locales\src\index.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import {
  i18n,
  loadLocaleMessages,
  loadLocalesMap,
  loadLocalesMapFromDir,
  loadNamespaceMessages,
  loadNamespacedLocalesMap,
  preloadLocaleOnIdle,
  setupI18n,
} from './i18n';

/**
 * 延迟绑定的翻译函数
 * @description 避免模块顶层直接绑定 i18n.global.t，确保 i18n 初始化后才调用
 */
function $t(...args: Parameters<typeof i18n.global.t>) {
  return i18n.global.t(...args);
}

function $te(...args: Parameters<typeof i18n.global.te>) {
  return i18n.global.te(...args);
}

export {
  $t,
  $te,
  i18n,
  loadLocaleMessages,
  loadLocalesMap,
  loadLocalesMapFromDir,
  loadNamespaceMessages,
  loadNamespacedLocalesMap,
  preloadLocaleOnIdle,
  setupI18n,
};
export {
  type ImportLocaleFn,
  type ImportNamespaceFn,
  type LocaleSetupOptions,
  type NamespacedLocalesMap,
  type PreloadLocaleOptions,
  type SupportedLanguagesType,
} from './typing';
export type { CompileError } from '@intlify/core-base';

export { useI18n } from 'vue-i18n';

export type { Locale } from 'vue-i18n';
