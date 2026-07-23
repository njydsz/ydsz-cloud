/**
 * @fileoverview Vue I18n 入口
 * @description 集成 vue-i18n v10，支持中英文切换（默认 zh-CN）。
 *               本文件仅负责 i18n 实例创建、locale 探测、setLocale / getLocale 暴露与 html lang 同步，
 *               实际文案由 ./zh-CN.ts 与 ./en-US.ts 提供；Element Plus 语言包由 App.vue 按当前 locale 动态切换。
 *
 * 维护规范：
 *  - 新增 / 修改文案应同时在 zh-CN.ts 与 en-US.ts 中按相同 key 路径补齐；任一语言缺失会触发 fallbackLocale 兜底。
 *  - 涉及参数化插值（如 {n}、{level}）时必须保证两套语言包占位符一致。
 *  - Locale 持久化键 STORAGE_KEY = 'ydsz-locale'，值仅允许 'zh-CN' | 'en-US'。
 *
 * Key 命名规范：
 *  - 顶层 key = 业务模块名（common / workflow / project / system / execution ...）。
 *  - 二级 key = 页面或子模块（approval / diagram / instance / user ...）。
 *  - 三级及更深 key = 具体文案（confirm / cancel / rules.roleCodeRequired ...）。
 *  - 全部小写 + 驼峰，禁止拼音 / 下划线 / 数字开头的 key。
 *
 * 与 i18n-config 的关系：
 *  - 本文件不直接依赖项目根的 i18n-config 目录；所有 messages 内联在此模块以避免 Vite 动态 import 的运行时分支。
 *  - 若后续接入独立 i18n-config（如 @/config/i18n），建议仅在入口层做"config 读取 → detectLocale → createI18n"对接，
 *    文案数据仍保留在 locales/* 下，避免双语同步失控。
 *
 * @module locales/index
 * @author ydsz-team
 * @since 1.0.0
 */
import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import enUS from './en-US'

export type LocaleType = 'zh-CN' | 'en-US'

const STORAGE_KEY = 'ydsz-locale'

/** 探测浏览器首选语言 */
function detectLocale(): LocaleType {
  try {
    const stored = localStorage.getItem(STORAGE_KEY) as LocaleType | null
    if (stored === 'zh-CN' || stored === 'en-US') {
      return stored
    }
    const nav = typeof navigator !== 'undefined' ? navigator.language : 'zh-CN'
    return nav.toLowerCase().startsWith('zh') ? 'zh-CN' : 'en-US'
  } catch {
    return 'zh-CN'
  }
}

const i18n = createI18n({
  legacy: false, // Composition API 模式
  globalInjection: true,
  locale: detectLocale(),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
})

/** 切换语言并持久化 */
export function setLocale(locale: LocaleType): void {
  i18n.global.locale.value = locale
  try {
    localStorage.setItem(STORAGE_KEY, locale)
  } catch {
    /* localStorage 可能不可用 */
  }
  document.documentElement.setAttribute('lang', locale)
}

/** 获取当前语言 */
export function getLocale(): LocaleType {
  return i18n.global.locale.value as LocaleType
}

// 初始化 html lang 属性（确保首次加载即正确设置）
if (typeof document !== 'undefined') {
  document.documentElement.setAttribute('lang', i18n.global.locale.value)
}

export default i18n
