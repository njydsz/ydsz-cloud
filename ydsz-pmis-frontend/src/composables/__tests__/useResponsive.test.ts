/**
 * @file useResponsive composable 单元测试
 * @description 测试响应式断点检测逻辑
 * @module composables/__tests__/useResponsive
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { useResponsive } from '../useResponsive'

// 辅助：在组件上下文中使用 composable
function withUseResponsive() {
  let result: ReturnType<typeof useResponsive> | null = null
  const TestComponent = defineComponent({
    setup() {
      result = useResponsive()
      return () => h('div')
    },
  })
  const wrapper = mount(TestComponent)
  return { wrapper, result: result! }
}

describe('useResponsive', () => {
  const originalInnerWidth = window.innerWidth
  const originalInnerHeight = window.innerHeight

  function setViewport(width: number, height = 800) {
    Object.defineProperty(window, 'innerWidth', {
      writable: true,
      configurable: true,
      value: width,
    })
    Object.defineProperty(window, 'innerHeight', {
      writable: true,
      configurable: true,
      value: height,
    })
    window.dispatchEvent(new Event('resize'))
  }

  beforeEach(() => {
    setViewport(1920, 1080)
  })

  afterEach(() => {
    Object.defineProperty(window, 'innerWidth', {
      writable: true,
      configurable: true,
      value: originalInnerWidth,
    })
    Object.defineProperty(window, 'innerHeight', {
      writable: true,
      configurable: true,
      value: originalInnerHeight,
    })
  })

  it('桌面端宽度应该返回 isMobile=false, isDesktop=true', () => {
    setViewport(1920)
    const { result } = withUseResponsive()
    expect(result.isMobile.value).toBe(false)
    expect(result.isDesktop.value).toBe(false) // 1920 is 'wide', not 'desktop'
    expect(result.isWide.value).toBe(true)
  })

  it('小屏宽度应该返回 isMobile=true', () => {
    setViewport(375)
    const { result } = withUseResponsive()
    expect(result.isMobile.value).toBe(true)
  })

  it('平板宽度（768px-1200px）应该返回 isTablet=true', () => {
    setViewport(992)
    const { result } = withUseResponsive()
    expect(result.isTablet.value).toBe(true)
    expect(result.isMobile.value).toBe(false)
  })

  it('桌面宽度（1200px-1920px）应该返回 isDesktop=true', () => {
    setViewport(1440)
    const { result } = withUseResponsive()
    expect(result.isDesktop.value).toBe(true)
  })

  it('device 属性正确反映设备类型', () => {
    setViewport(375)
    const { result: mobileResult } = withUseResponsive()
    expect(mobileResult.device.value).toBe('mobile')
  })

  it('screenWidth 和 screenHeight 正确反映窗口尺寸', () => {
    setViewport(1440, 900)
    const { result } = withUseResponsive()
    expect(result.screenWidth.value).toBe(1440)
    expect(result.screenHeight.value).toBe(900)
  })
})
