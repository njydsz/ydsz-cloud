/**
 * @file ErrorBoundary 全局错误边界组件 单元测试（P2-1）
 * @description 验证 ErrorBoundary 正常时渲染 slot、子组件抛错时展示降级 UI。
 * @module components/common/__tests__/ErrorBoundary
 */
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { h, defineComponent } from 'vue'
import ErrorBoundary from '../ErrorBoundary.vue'

// Mock sentry captureError 避免测试环境真正调用
vi.mock('@/utils/sentry', () => ({
  captureError: vi.fn(),
}))

// Element Plus el-result / el-button stub
const elStubs = {
  'el-result': {
    template: '<div class="el-result-stub"><slot name="extra" /></div>',
    props: ['icon', 'title', 'subTitle'],
  },
  'el-button': {
    template: '<button class="el-button-stub" @click="$emit(\'click\')"><slot /></button>',
  },
}

describe('ErrorBoundary 全局错误边界', () => {
  it('正常状态渲染 slot 内容', () => {
    const wrapper = mount(ErrorBoundary, {
      slots: { default: '<div class="content">Hello</div>' },
      global: { stubs: elStubs },
    })
    expect(wrapper.find('.content').exists()).toBe(true)
    expect(wrapper.find('.error-boundary').exists()).toBe(false)
  })

  it('子组件抛错时展示降级 UI', async () => {
    const Boom = defineComponent({
      name: 'Boom',
      setup() {
        throw new Error('render boom')
      },
      render() {
        return h('div', 'should not render')
      },
    })

    const wrapper = mount(ErrorBoundary, {
      slots: { default: () => h(Boom) },
      global: { stubs: elStubs },
    })

    // 等待 nextTick
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.error-boundary').exists()).toBe(true)
    expect(wrapper.find('.el-result-stub').exists()).toBe(true)
  })

  it('点击重试按钮重置错误状态', async () => {
    // 使用可控组件：首次抛错，重试后正常渲染
    let shouldBoom = true
    const ConditionalBoom = defineComponent({
      name: 'ConditionalBoom',
      setup() {
        if (shouldBoom) {
          shouldBoom = false
          throw new Error('boom')
        }
        return () => h('div', { class: 'safe-content' }, 'recovered')
      },
    })

    const wrapper = mount(ErrorBoundary, {
      slots: { default: () => h(ConditionalBoom) },
      global: { stubs: elStubs },
    })

    await wrapper.vm.$nextTick()
    expect(wrapper.find('.error-boundary').exists()).toBe(true)

    // 点击重试
    const buttons = wrapper.findAll('button.el-button-stub')
    expect(buttons.length).toBeGreaterThanOrEqual(1)
    await buttons[0].trigger('click')
    await wrapper.vm.$nextTick()

    // 错误状态被重置，slot 正常渲染
    expect(wrapper.find('.error-boundary').exists()).toBe(false)
    expect(wrapper.find('.safe-content').exists()).toBe(true)
  })
})
