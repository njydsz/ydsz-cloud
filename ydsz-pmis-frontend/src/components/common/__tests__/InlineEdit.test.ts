/**
 * @file InlineEdit 行内编辑 单元测试 (P2-15)
 * @description 覆盖只读态展示、占位符、双击进入编辑、禁用态、Enter 提交、Escape 取消.
 *   el-icon 已在 tests/setup.ts 全局注册; el-input 用桩组件替身:
 *   - 单根 <input>, 让父级 @keydown / @blur / v-model 透传到原生 input, 便于 trigger keydown
 *   - 不实现 focus(), 避免卸载时 blur 触发 commit 副作用, 保证 Escape 用例确定
 * @module components/common/__tests__/InlineEdit
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import InlineEdit from '@/components/common/InlineEdit.vue'

/** el-input 桩: 单根原生 input, 透传父级监听; 声明 size/type/placeholder 等 prop 避免透传到原生 input 触发 DOM 警告; 不实现 focus 避免卸载 blur 副作用 */
const ElInputStub = {
  name: 'ElInput',
  template: '<input class="el-input-stub" :value="modelValue" />',
  props: {
    modelValue: { default: '' },
    type: { default: 'text' },
    placeholder: { default: '' },
    size: { default: 'default' },
  },
}

/** el-select / el-option / el-date-picker 桩: 仅占位避免 resolve 警告(text 模式不会渲染) */
const ElSelectStub = { name: 'ElSelect', template: '<div class="el-select-stub" />' }
const ElOptionStub = { name: 'ElOption', template: '<div class="el-option-stub" />' }
const ElDatePickerStub = {
  name: 'ElDatePicker',
  template: '<div class="el-date-picker-stub" />',
}

const globalConfig = {
  components: {
    ElInput: ElInputStub,
    ElSelect: ElSelectStub,
    ElOption: ElOptionStub,
    ElDatePicker: ElDatePickerStub,
  },
}

describe('InlineEdit 行内编辑', () => {
  it('只读态展示 modelValue', () => {
    const wrapper = mount(InlineEdit, {
      props: { modelValue: 'test value' },
      global: globalConfig,
    })
    expect(wrapper.find('.display-value').text()).toContain('test value')
  })

  it('值为空时展示 placeholder', () => {
    const wrapper = mount(InlineEdit, {
      props: { modelValue: '', placeholder: '请输入' },
      global: globalConfig,
    })
    expect(wrapper.find('.display-value').text()).toContain('请输入')
  })

  it('双击进入编辑态', async () => {
    const wrapper = mount(InlineEdit, {
      props: { modelValue: 'test' },
      global: globalConfig,
    })
    expect(wrapper.findComponent({ name: 'ElInput' }).exists()).toBe(false)
    await wrapper.find('.display-value').trigger('dblclick')
    expect(wrapper.findComponent({ name: 'ElInput' }).exists()).toBe(true)
  })

  it('禁用态不可进入编辑', async () => {
    const wrapper = mount(InlineEdit, {
      props: { modelValue: 'test', disabled: true },
      global: globalConfig,
    })
    await wrapper.find('.display-value').trigger('dblclick')
    expect(wrapper.findComponent({ name: 'ElInput' }).exists()).toBe(false)
  })

  it('Enter 提交触发 commit 事件', async () => {
    const wrapper = mount(InlineEdit, {
      props: { modelValue: 'old' },
      global: globalConfig,
    })
    await wrapper.find('.display-value').trigger('dblclick')
    await wrapper.find('input').trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('commit')).toBeTruthy()
  })

  it('Escape 取消编辑且不触发 commit', async () => {
    const wrapper = mount(InlineEdit, {
      props: { modelValue: 'original' },
      global: globalConfig,
    })
    await wrapper.find('.display-value').trigger('dblclick')
    await wrapper.find('input').trigger('keydown', { key: 'Escape' })
    expect(wrapper.findComponent({ name: 'ElInput' }).exists()).toBe(false)
    expect(wrapper.emitted('commit')).toBeFalsy()
  })
})
