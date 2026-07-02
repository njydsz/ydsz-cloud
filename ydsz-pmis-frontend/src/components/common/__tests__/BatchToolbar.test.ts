/**
 * @file BatchToolbar 批量操作工具栏组件 单元测试（P1-8）
 * @description 覆盖 selectedCount=0 不渲染、selectedCount>0 渲染并显示数量、
 *   操作按钮渲染、清空选择事件派发等场景.
 * @module components/common/__tests__/BatchToolbar
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BatchToolbar from '../BatchToolbar.vue'

describe('BatchToolbar', () => {
  it('should not render when selectedCount is 0', () => {
    const wrapper = mount(BatchToolbar, {
      props: { selectedCount: 0, actions: [] },
    })
    expect(wrapper.find('.batch-toolbar').exists()).toBe(false)
  })

  it('should render when selectedCount > 0', () => {
    const wrapper = mount(BatchToolbar, {
      props: {
        selectedCount: 3,
        actions: [{ label: '批量删除', type: 'danger', handler: () => {} }],
      },
    })
    expect(wrapper.find('.batch-toolbar').exists()).toBe(true)
    expect(wrapper.text()).toContain('3')
  })

  it('should render all action buttons', () => {
    const wrapper = mount(BatchToolbar, {
      props: {
        selectedCount: 2,
        actions: [
          { label: '批量审批', type: 'primary', handler: () => {} },
          { label: '批量导出', handler: () => {} },
        ],
      },
    })
    const buttons = wrapper.findAll('.batch-actions .el-button')
    expect(buttons.length).toBeGreaterThanOrEqual(2)
  })

  it('should emit clear when clear button clicked', async () => {
    const wrapper = mount(BatchToolbar, {
      props: { selectedCount: 1, actions: [] },
    })
    await wrapper.find('.batch-info .el-button').trigger('click')
    expect(wrapper.emitted('clear')).toBeTruthy()
  })
})
