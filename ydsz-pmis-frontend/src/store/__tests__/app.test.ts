/**
 * @file App Store 单元测试
 * @description 测试全局 UI 状态管理：侧边栏折叠、主题切换、缓存视图
 * @module store/__tests__/app
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAppStore } from '../modules/app'

describe('useAppStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    // 重置 document classList
    document.documentElement.classList.remove('dark')
  })

  afterEach(() => {
    localStorage.clear()
    document.documentElement.classList.remove('dark')
  })

  describe('侧边栏', () => {
    it('初始状态为未折叠', () => {
      const store = useAppStore()
      expect(store.sidebarCollapsed).toBe(false)
    })

    it('toggleSidebar 切换折叠状态', () => {
      const store = useAppStore()
      expect(store.sidebarCollapsed).toBe(false)
      store.toggleSidebar()
      expect(store.sidebarCollapsed).toBe(true)
      store.toggleSidebar()
      expect(store.sidebarCollapsed).toBe(false)
    })

    it('折叠状态持久化到 localStorage', () => {
      const store = useAppStore()
      store.toggleSidebar()
      expect(localStorage.getItem('sidebarCollapsed')).toBe('true')
    })

    it('从 localStorage 恢复折叠状态', () => {
      localStorage.setItem('sidebarCollapsed', 'true')
      // 需要重新创建 pinia 实例以触发 store 重新初始化
      setActivePinia(createPinia())
      const store = useAppStore()
      expect(store.sidebarCollapsed).toBe(true)
    })
  })

  describe('主题', () => {
    it('初始主题为 light', () => {
      const store = useAppStore()
      expect(store.theme).toBe('light')
    })

    it('toggleTheme 切换为 dark', () => {
      const store = useAppStore()
      store.toggleTheme()
      expect(store.theme).toBe('dark')
      expect(document.documentElement.classList.contains('dark')).toBe(true)
    })

    it('toggleTheme 再次切换回 light', () => {
      const store = useAppStore()
      store.toggleTheme() // -> dark
      store.toggleTheme() // -> light
      expect(store.theme).toBe('light')
      expect(document.documentElement.classList.contains('dark')).toBe(false)
    })

    it('主题持久化到 localStorage', () => {
      const store = useAppStore()
      store.toggleTheme()
      expect(localStorage.getItem('theme')).toBe('dark')
    })

    it('initTheme 从 localStorage 恢复 dark 主题', () => {
      localStorage.setItem('theme', 'dark')
      const store = useAppStore()
      store.initTheme()
      expect(store.theme).toBe('dark')
      expect(document.documentElement.classList.contains('dark')).toBe(true)
    })

    it('initTheme 无记录时默认 light', () => {
      const store = useAppStore()
      store.initTheme()
      expect(store.theme).toBe('light')
    })
  })

  describe('缓存视图', () => {
    it('初始为空数组', () => {
      const store = useAppStore()
      expect(store.cachedViews).toEqual([])
    })

    it('addCachedView 添加视图名称', () => {
      const store = useAppStore()
      store.addCachedView('Dashboard')
      expect(store.cachedViews).toContain('Dashboard')
    })

    it('addCachedView 不重复添加', () => {
      const store = useAppStore()
      store.addCachedView('Dashboard')
      store.addCachedView('Dashboard')
      expect(store.cachedViews).toHaveLength(1)
    })

    it('removeCachedView 移除指定视图', () => {
      const store = useAppStore()
      store.addCachedView('Dashboard')
      store.addCachedView('Profile')
      store.removeCachedView('Dashboard')
      expect(store.cachedViews).not.toContain('Dashboard')
      expect(store.cachedViews).toContain('Profile')
    })

    it('clearCachedViews 清空所有视图', () => {
      const store = useAppStore()
      store.addCachedView('Dashboard')
      store.addCachedView('Profile')
      store.clearCachedViews()
      expect(store.cachedViews).toEqual([])
    })
  })

  describe('设备与尺寸', () => {
    it('setDevice 设置设备类型', () => {
      const store = useAppStore()
      store.setDevice('mobile')
      expect(store.device).toBe('mobile')
    })

    it('setSize 设置组件尺寸', () => {
      const store = useAppStore()
      store.setSize('small')
      expect(store.size).toBe('small')
    })
  })
})
