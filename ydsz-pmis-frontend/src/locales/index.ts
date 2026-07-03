/**
 * @file Vue I18n 入口
 * @description 集成 vue-i18n v10，支持中英文切换（默认 zh-CN）
 * @module locales/index
 *
 * 用法：
 *  - 在 main.ts 中 `app.use(i18n)`
 *  - 组件中 `const { t } = useI18n(); t('workflow.diagram.legend.completed')`
 *  - 切换语言：i18n.global.locale.value = 'en-US'
 *  - localStorage 记忆：localeStorage.getItem('pmis-locale')
 *
 * P2-4 落地：与流程图回放组件配套，先提供中英文基础文案。
 */
import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import enUS from './en-US'

export type LocaleType = 'zh-CN' | 'en-US'

const STORAGE_KEY = 'pmis-locale'

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
