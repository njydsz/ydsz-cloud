/**
 * @file FormDialog 通用表单弹窗组件 单元测试
 * @description 覆盖 FormDialog 的 modelValue 双向绑定、title props 透传、
 *   以及 exposed 的 validate / clearValidate / resetFields 在 formRef 缺失时的兜底行为.
 * @module components/common/__tests__/FormDialog
 */
import { describe, it, expect } from 'vitest'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import FormDialog from '@/components/common/FormDialog.vue'

describe('FormDialog 通用表单弹窗', () => {
  it('应正确接收 modelValue / title 等 props', () => {
    const wrapper = mount(FormDialog, {
      props: { modelValue: true, title: '测试标题' },
    })
    // el-dialog 走 teleport, jsdom 中可能未渲染, 这里仅校验 props 传递
    expect(wrapper.props('modelValue')).toBe(true)
    expect(wrapper.props('title')).toBe('测试标题')
  })

  it('visible 同步后父组件收到 update:modelValue', async () => {
    const wrapper = mount(FormDialog, {
      props: { modelValue: true, title: 't' },
    })
    await wrapper.setProps({ modelValue: false })
    await nextTick()
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
  })

  it('exposed.validate 在 formRef 不存在时直接返回 true', () => {
    const wrapper = mount(FormDialog, {
      props: { modelValue: true, title: 't' },
    })
    const exposed = wrapper.vm.$.exposed as { validate: () => Promise<boolean> }
    return expect(exposed.validate()).resolves.toBe(true)
  })

  it('exposed.clearValidate / resetFields 在 formRef 缺失时不抛错', () => {
    const wrapper = mount(FormDialog, {
      props: { modelValue: true, title: 't' },
    })
    const exposed = wrapper.vm.$.exposed as {
      clearValidate: () => void
      resetFields: () => void
    }
    expect(() => exposed.clearValidate()).not.toThrow()
    expect(() => exposed.resetFields()).not.toThrow()
  })
})
