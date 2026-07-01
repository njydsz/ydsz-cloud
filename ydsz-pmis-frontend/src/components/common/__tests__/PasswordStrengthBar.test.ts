import { describe, it, expect, vi } from 'vitest'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import PasswordStrengthBar from '@/components/common/PasswordStrengthBar.vue'

// Stub el-input to capture v-model
const ElInputStub = {
  props: ['modelValue', 'type', 'showPassword', 'placeholder'],
  emits: ['update:modelValue'],
  template:
    '<input class="el-input-stub" :value="modelValue" :type="type" @input="$emit(\'update:modelValue\', $event.target.value)" />',
}

describe('PasswordStrengthBar 5 段强度条', () => {
  it('应正确接收 modelValue', () => {
    const wrapper = mount(PasswordStrengthBar, {
      props: { modelValue: 'secret123' },
      global: { components: { 'el-input': ElInputStub } },
    })
    expect(wrapper.props('modelValue')).toBe('secret123')
  })

  it('v-model 双向绑定', async () => {
    const wrapper = mount(PasswordStrengthBar, {
      props: { modelValue: '' },
      global: { components: { 'el-input': ElInputStub } },
    })
    const input = wrapper.find('input.el-input-stub')
    await input.setValue('Aa1!aaaa')
    await nextTick()
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['Aa1!aaaa'])
  })

  it('5 段 seg 渲染', () => {
    const wrapper = mount(PasswordStrengthBar, {
      props: { modelValue: '', showInput: false },
    })
    const segs = wrapper.findAll('.seg')
    expect(segs).toHaveLength(5)
  })

  it('空密码时 0 段点亮', () => {
    const wrapper = mount(PasswordStrengthBar, {
      props: { modelValue: '', showInput: false },
    })
    const onSegs = wrapper.findAll('.seg.on')
    expect(onSegs).toHaveLength(0)
  })

  it('强密码时 4 段点亮', () => {
    const wrapper = mount(PasswordStrengthBar, {
      props: { modelValue: 'Aa1!aaaa', showInput: false },
    })
    const onSegs = wrapper.findAll('.seg.on')
    expect(onSegs).toHaveLength(4)
  })

  it('showRules=true 时显示规则明细', () => {
    const wrapper = mount(PasswordStrengthBar, {
      props: { modelValue: 'Aa1!aaaa', showInput: false, showRules: true },
    })
    const rules = wrapper.findAll('.rules li')
    expect(rules).toHaveLength(4)
  })

  it('showRules=true 弱密码规则不通过', () => {
    const wrapper = mount(PasswordStrengthBar, {
      props: { modelValue: 'aaaaaaaa', showInput: false, showRules: true },
    })
    const passRules = wrapper.findAll('.rules li.pass')
    expect(passRules.length).toBeLessThan(4)
  })

  it('compact 模式不显示 level/score', () => {
    const wrapper = mount(PasswordStrengthBar, {
      props: { modelValue: 'Aa1!aaaa', showInput: false, compact: true },
    })
    expect(wrapper.find('.meta').exists()).toBe(false)
  })

  it('change 事件派发', async () => {
    const wrapper = mount(PasswordStrengthBar, {
      props: { modelValue: '' },
      global: { components: { 'el-input': ElInputStub } },
    })
    const input = wrapper.find('input.el-input-stub')
    await input.setValue('Aa1!aaaa')
    await nextTick()
    expect(wrapper.emitted('change')).toBeTruthy()
  })

  it('纯展示模式：只更新 :password 即可', async () => {
    const wrapper = mount(PasswordStrengthBar, {
      props: { password: 'weak', showInput: false },
    })
    expect(wrapper.props('password')).toBe('weak')
    await wrapper.setProps({ password: 'Aa1!aaaa' })
    await nextTick()
    const onSegs = wrapper.findAll('.seg.on')
    expect(onSegs.length).toBe(4)
  })
})
