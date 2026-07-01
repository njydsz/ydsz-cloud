/**
 * @file App Store - 全局 UI 状态管理
 * @description 管理侧边栏折叠状态、设备类型、组件尺寸、主题等 UI 相关全局状态
 * @module store/modules/app
 *
 * 与业务 store 区分：
 *  - app store 只管 UI 状态（不参与权限与数据持久化）
 *  - 主题切换会直接操作 document.documentElement.classList，影响 Element Plus 暗黑模式
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'

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
   * 同时操作 document.documentElement.classList 以触发 Element Plus 暗黑模式 CSS 变量
   */
  function toggleTheme(): void {
    theme.value = theme.value === 'light' ? 'dark' : 'light'
    document.documentElement.classList.toggle('dark', theme.value === 'dark')
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
  }
})
