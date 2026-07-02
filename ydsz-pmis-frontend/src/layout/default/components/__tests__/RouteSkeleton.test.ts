/**
 * @file RouteSkeleton 骨架屏组件 单元测试（P1-14）
 * @description 验证路由懒加载骨架屏的 DOM 结构与元素数量，
 *   确保用户在路由切换时看到合理的占位布局而非白屏。
 * @module layout/default/components/__tests__/RouteSkeleton
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import RouteSkeleton from '../RouteSkeleton.vue'

describe('RouteSkeleton 路由骨架屏', () => {
  it('渲染根容器并带有 aria-label 无障碍标记', () => {
    const wrapper = mount(RouteSkeleton)
    const root = wrapper.find('.route-skeleton')
    expect(root.exists()).toBe(true)
    expect(root.attributes('role')).toBe('status')
    expect(root.attributes('aria-label')).toBe('页面加载中')
  })

  it('渲染标题栏占位（title + action）', () => {
    const wrapper = mount(RouteSkeleton)
    const header = wrapper.find('.skeleton-header')
    expect(header.exists()).toBe(true)
    expect(header.find('.skeleton-title').exists()).toBe(true)
    expect(header.find('.skeleton-action').exists()).toBe(true)
  })

  it('渲染 3 个筛选项 + 1 个筛选按钮', () => {
    const wrapper = mount(RouteSkeleton)
    const filter = wrapper.find('.skeleton-filter')
    expect(filter.exists()).toBe(true)
    expect(filter.findAll('.skeleton-filter-item')).toHaveLength(3)
    expect(filter.findAll('.skeleton-filter-btn')).toHaveLength(1)
  })

  it('渲染 8 行表格占位，每行 5 列', () => {
    const wrapper = mount(RouteSkeleton)
    const table = wrapper.find('.skeleton-table')
    expect(table.exists()).toBe(true)
    const rows = table.findAll('.skeleton-row')
    expect(rows).toHaveLength(8)
    rows.forEach((row) => {
      expect(row.findAll('.skeleton-cell')).toHaveLength(5)
    })
  })

  it('所有骨架条均带有 shimmer 动画类', () => {
    const wrapper = mount(RouteSkeleton)
    const bars = wrapper.findAll('.skeleton-bar')
    expect(bars.length).toBeGreaterThan(0)
    bars.forEach((bar) => {
      expect(bar.classes()).toContain('skeleton-bar')
    })
  })
})
