/**
 * @file App Store - 全局 UI 状态管理
 * @description 管理侧边栏折叠状态、设备类型、组件尺寸、主题等 UI 相关全局状态
 * @module store/modules/app
 *
 * 与业务 store 区分：
 *  - app store 只管 UI 状态（不参与权限与数据持久化）
 *  - 主题切换会直接操作 document.documentElement.classList，影响 Element Plus 暗黑模式
 *  - 主题选择持久化到 localStorage，刷新后通过 initTheme() 恢复
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'

/** localStorage 中存储主题的 key */
const THEME_STORAGE_KEY = 'theme'

export const useAppStore = defineStore('app', () => {
  /** 侧边栏是否折叠（窄模式） */
  const sidebarCollapsed = ref(false)
  /** 当前设备类型，影响 layout 渲染策略 */
  const device = ref<'desktop' | 'mobile'>('desktop')
  /** Element Plus 组件全局尺寸 */
  const size = ref<'default' | 'large' | 'small'>('default')
  /** 当前主题（light 浅色 / dark 暗色） */
  const theme = ref<'light' | 'dark'>('light')

  /** 切换侧边栏折叠状态 */
  function toggleSidebar(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  /** 设置设备类型（响应式断点变化时由 layout 调用） */
  function setDevice(d: 'desktop' | 'mobile'): void {
    device.value = d
  }

  /** 设置全局组件尺寸 */
  function setSize(s: 'default' | 'large' | 'small'): void {
    size.value = s
  }

  /**
   * 切换主题（light ↔ dark）
   *
   * 同时操作 document.documentElement.classList 以触发 Element Plus 暗黑模式 CSS 变量，
   * 并将新主题持久化到 localStorage，刷新后通过 initTheme() 恢复。
   */
  function toggleTheme(): void {
    const newTheme = theme.value === 'light' ? 'dark' : 'light'
    theme.value = newTheme
    document.documentElement.classList.toggle('dark', newTheme === 'dark')
    try {
      localStorage.setItem(THEME_STORAGE_KEY, newTheme)
    } catch {
      /* localStorage 可能不可用（如隐私模式） */
    }
  }

  /**
   * 初始化主题（应用启动时调用）
   *
   * 从 localStorage 读取用户上次选择的主题，若无记录则默认 light。
   * 若为 dark 则给 <html> 添加 dark class，触发 Element Plus 暗黑模式与自定义 CSS 变量覆盖。
   */
  function initTheme(): void {
    let saved: 'light' | 'dark' = 'light'
    try {
      const stored = localStorage.getItem(THEME_STORAGE_KEY)
      if (stored === 'dark' || stored === 'light') {
        saved = stored
      }
    } catch {
      /* localStorage 可能不可用 */
    }
    theme.value = saved
    document.documentElement.classList.toggle('dark', saved === 'dark')
  }

  return {
    sidebarCollapsed,
    device,
    size,
    theme,
    toggleSidebar,
    setDevice,
    setSize,
    toggleTheme,
    initTheme,
  }
})
