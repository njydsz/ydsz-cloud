/**
 * @file i18n 兼容层（已废弃，请直接使用 vue-i18n）
 * @description 此文件为向后兼容保留的 re-export 层。
 *              原自研 i18n 系统已统一到 vue-i18n，所有新代码应直接使用：
 *                import { useI18n } from 'vue-i18n'
 *                import { setLocale, getLocale } from '@/locales'
 * @module composables/useI18n
 * @deprecated 迁移至 vue-i18n + @/locales
 */
import { useI18n as vueUseI18n } from 'vue-i18n'
import { setLocale, getLocale, type LocaleType } from '@/locales'

export type Locale = LocaleType

/** 支持的语言列表 */
export const supportedLocales: ReadonlyArray<{ code: Locale; label: string }> = [
  { code: 'zh-CN', label: '简体中文' },
  { code: 'en-US', label: 'English' },
]

/**
 * @deprecated 请直接使用 vue-i18n 的 useI18n
 * 兼容包装：返回 vue-i18n 的 useI18n + locales/index 的 setLocale
 */
export function useI18n() {
  const { t, locale } = vueUseI18n()
  return {
    locale,
    supportedLocales,
    t,
    setLocale,
  }
}

/** @deprecated 请从 @/locales 导入 */
export { setLocale, getLocale }

/**
 * @deprecated 请使用 vue-i18n 的 locale
 * 向后兼容的 locale 访问器
 */
export const locale = {
  get value() {
    return getLocale()
  },
}
