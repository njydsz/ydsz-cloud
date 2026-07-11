/**
 * @file useKeyboardA11y 单元测试
 * @description 测试键盘无障碍辅助函数的行为正确性
 */
import { describe, it, expect, vi } from 'vitest'
import { onKeyActivate, vA11yClick } from '@/composables/useKeyboardA11y'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'

describe('onKeyActivate', () => {
  it('应该在 Enter 键时触发回调', () => {
    const handler = vi.fn()
    const keyHandler = onKeyActivate(handler)

    const event = new KeyboardEvent('keydown', { key: 'Enter' })
    Object.defineProperty(event, 'preventDefault', { value: vi.fn() })
    Object.defineProperty(event, 'stopPropagation', { value: vi.fn() })
    keyHandler(event)

    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('应该在 Space 键时触发回调', () => {
    const handler = vi.fn()
    const keyHandler = onKeyActivate(handler)

    const event = new KeyboardEvent('keydown', { key: ' ' })
    Object.defineProperty(event, 'preventDefault', { value: vi.fn() })
    Object.defineProperty(event, 'stopPropagation', { value: vi.fn() })
    keyHandler(event)

    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('应该在 Spacebar 键时触发回调（兼容旧浏览器）', () => {
    const handler = vi.fn()
    const keyHandler = onKeyActivate(handler)

    const event = new KeyboardEvent('keydown', { key: 'Spacebar' })
    Object.defineProperty(event, 'preventDefault', { value: vi.fn() })
    Object.defineProperty(event, 'stopPropagation', { value: vi.fn() })
    keyHandler(event)

    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('不应该在非 Enter/Space 键时触发回调', () => {
    const handler = vi.fn()
    const keyHandler = onKeyActivate(handler)

    const event = new KeyboardEvent('keydown', { key: 'Tab' })
    keyHandler(event)

    expect(handler).not.toHaveBeenCalled()
  })

  it('应该调用 preventDefault 和 stopPropagation', () => {
    const handler = vi.fn()
    const keyHandler = onKeyActivate(handler)

    const preventDefault = vi.fn()
    const stopPropagation = vi.fn()
    const event = new KeyboardEvent('keydown', { key: 'Enter' })
    Object.defineProperty(event, 'preventDefault', { value: preventDefault })
    Object.defineProperty(event, 'stopPropagation', { value: stopPropagation })
    keyHandler(event)

    expect(preventDefault).toHaveBeenCalledTimes(1)
    expect(stopPropagation).toHaveBeenCalledTimes(1)
  })
})

describe('vA11yClick directive', () => {
  it('应该在 mounted 时绑定 keydown 事件', () => {
    const handler = vi.fn()
    const TestComponent = defineComponent({
      directives: { a11yClick: vA11yClick },
      render() {
        return h('div', { 'v-a11y-click': handler, role: 'button', tabindex: '0' })
      },
    })

    const wrapper = mount(TestComponent)
    const el = wrapper.element as HTMLElement

    // 模拟 keydown 事件
    const event = new KeyboardEvent('keydown', { key: 'Enter' })
    Object.defineProperty(event, 'preventDefault', { value: vi.fn() })
    Object.defineProperty(event, 'stopPropagation', { value: vi.fn() })
    el.dispatchEvent(event)

    // 由于 Vue 指令绑定机制，这里验证元素有 __a11yKeyHandler__
    expect(el.__a11yKeyHandler__).toBeDefined()
    wrapper.unmount()
  })

  it('应该在 unmounted 时移除 keydown 事件', () => {
    const handler = vi.fn()
    const TestComponent = defineComponent({
      directives: { a11yClick: vA11yClick },
      render() {
        return h('div', { 'v-a11y-click': handler, role: 'button', tabindex: '0' })
      },
    })

    const wrapper = mount(TestComponent)
    const el = wrapper.element as HTMLElement

    expect(el.__a11yKeyHandler__).toBeDefined()
    wrapper.unmount()
    expect(el.__a11yKeyHandler__).toBeUndefined()
  })
})
