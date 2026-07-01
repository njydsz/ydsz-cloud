/**
 * @file i18n 国际化 composable
 * @description 轻量级实现，不引入 vue-i18n 大依赖；支持 zh-CN / en-US 切换、变量插值、localStorage 持久化
 * @module composables/useI18n
 *
 * (批次 20 P2-2)
 *
 * 特性:
 *   1. 简单 key-value 翻译, 支持嵌套对象
 *   2. 动态切换语言 (zh-CN / en-US)
 *   3. 变量插值 {name}
 *   4. 缺失 key 时降级为 key 本身 (开发友好)
 *   5. localStorage 持久化用户选择
 *
 * 用法:
 *   const { t, locale, setLocale } = useI18n()
 *   t('common.save')               -> '保存'
 *   t('user.welcome', { name })    -> '欢迎, admin'
 */
import { ref, computed, readonly } from 'vue'

export type Locale = 'zh-CN' | 'en-US'

export interface TranslationDict {
  [key: string]: string | TranslationDict
}

const STORAGE_KEY = 'pmis_locale'
const DEFAULT_LOCALE: Locale = 'zh-CN'

const messages: Record<Locale, TranslationDict> = {
  'zh-CN': {
    common: {
      save: '保存',
      cancel: '取消',
      confirm: '确认',
      delete: '删除',
      edit: '编辑',
      add: '新增',
      search: '搜索',
      reset: '重置',
      refresh: '刷新',
      export: '导出',
      import: '导入',
      loading: '加载中...',
      success: '操作成功',
      failed: '操作失败',
      yes: '是',
      no: '否',
    },
    menu: {
      project: '项目管理',
      execution: '执行管理',
      finance: '财务管理',
      report: '报表中心',
      cockpit: '驾驶舱',
      system: '系统管理',
    },
    user: {
      welcome: '欢迎, {name}',
      login: '登录',
      logout: '登出',
      profile: '个人中心',
    },
    error: {
      network: '网络异常',
      unauthorized: '登录已过期, 请重新登录',
      forbidden: '没有权限',
      notFound: '资源不存在',
      serverError: '服务器异常',
    },
  },
  'en-US': {
    common: {
      save: 'Save',
      cancel: 'Cancel',
      confirm: 'Confirm',
      delete: 'Delete',
      edit: 'Edit',
      add: 'Add',
      search: 'Search',
      reset: 'Reset',
      refresh: 'Refresh',
      export: 'Export',
      import: 'Import',
      loading: 'Loading...',
      success: 'Success',
      failed: 'Failed',
      yes: 'Yes',
      no: 'No',
    },
    menu: {
      project: 'Projects',
      execution: 'Execution',
      finance: 'Finance',
      report: 'Reports',
      cockpit: 'Cockpit',
      system: 'System',
    },
    user: {
      welcome: 'Welcome, {name}',
      login: 'Login',
      logout: 'Logout',
      profile: 'Profile',
    },
    error: {
      network: 'Network error',
      unauthorized: 'Session expired, please login again',
      forbidden: 'Forbidden',
      notFound: 'Not found',
      serverError: 'Server error',
    },
  },
}

const _locale = ref<Locale>(loadLocale())

function loadLocale(): Locale {
  if (typeof window === 'undefined') return DEFAULT_LOCALE
  const stored = window.localStorage.getItem(STORAGE_KEY)
  if (stored === 'zh-CN' || stored === 'en-US') return stored
  // 根据浏览器语言自动选择
  const lang = window.navigator?.language
  if (lang?.startsWith('zh')) return 'zh-CN'
  return DEFAULT_LOCALE
}

function getNested(obj: TranslationDict, path: string): string | undefined {
  const keys = path.split('.')
  let cur: string | TranslationDict = obj
  for (const k of keys) {
    if (typeof cur !== 'object' || cur === null) return undefined
    cur = cur[k] as string | TranslationDict
  }
  return typeof cur === 'string' ? cur : undefined
}

function interpolate(template: string, vars?: Record<string, string | number>): string {
  if (!vars) return template
  return template.replace(/\{(\w+)\}/g, (_, k) =>
    Object.prototype.hasOwnProperty.call(vars, k) ? String(vars[k]) : `{${k}}`,
  )
}

/**
 * 翻译 key: t('common.save') 或 t('user.welcome', { name: 'admin' })
 */
function t(key: string, vars?: Record<string, string | number>): string {
  const dict = messages[_locale.value]
  const text = getNested(dict, key)
  if (text === undefined) {
    // 开发环境打印警告, 防止静默丢失
    if (import.meta.env.DEV) {
      // eslint-disable-next-line no-console
      console.warn(`[i18n] missing key: ${key}`)
    }
    return key
  }
  return interpolate(text, vars)
}

/**
 * 切换语言
 */
function setLocale(loc: Locale): void {
  _locale.value = loc
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(STORAGE_KEY, loc)
  }
  // 更新 document title 等
  if (typeof document !== 'undefined') {
    document.documentElement.lang = loc
  }
}

/**
 * 获取当前所有支持的语言
 */
export const supportedLocales: ReadonlyArray<{ code: Locale; label: string }> = [
  { code: 'zh-CN', label: '简体中文' },
  { code: 'en-US', label: 'English' },
]

/**
 * i18n composable
 */
export function useI18n() {
  return {
    locale: readonly(_locale),
    supportedLocales,
    t,
    setLocale,
  }
}

/** 当前 locale ref (供 useI18n() 之外的场景使用) */
export const locale = readonly(_locale)

/** reactive computed 当前 locale (响应式版本) */
export const currentLocale = computed(() => _locale.value)

export { t, setLocale, messages }
