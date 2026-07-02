/**
 * @file CustomDashboard 可定制仪表盘 单元测试 (P2-12)
 * @description 覆盖预设渲染、编辑模式切换、删除小部件等核心交互.
 *   el-button / el-icon / el-skeleton 已在 tests/setup.ts 全局注册,
 *   Close 图标在组件 <script setup> 内导入, 无需额外注册.
 * @module components/common/__tests__/CustomDashboard
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import CustomDashboard from '@/components/common/CustomDashboard.vue'

describe('CustomDashboard 可定制仪表盘', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('根据 preset 渲染小部件', () => {
    const wrapper = mount(CustomDashboard, {
      props: { preset: 'PM' },
    })
    expect(wrapper.findAll('.dashboard-widget').length).toBeGreaterThan(0)
  })

  it('可切换编辑模式', async () => {
    const wrapper = mount(CustomDashboard, {
      props: { preset: 'PM', editable: true },
    })
    expect(wrapper.find('.dashboard-grid.editing').exists()).toBe(false)
    await wrapper.find('.dashboard-toolbar .el-button').trigger('click')
    expect(wrapper.find('.dashboard-grid.editing').exists()).toBe(true)
  })

  it('编辑模式下可删除小部件', async () => {
    const wrapper = mount(CustomDashboard, {
      props: { preset: 'PM', editable: true },
    })
    await wrapper.find('.dashboard-toolbar .el-button').trigger('click')
    const initialCount = wrapper.findAll('.dashboard-widget').length
    await wrapper.find('.widget-remove').trigger('click')
    expect(wrapper.findAll('.dashboard-widget').length).toBe(initialCount - 1)
  })
})
