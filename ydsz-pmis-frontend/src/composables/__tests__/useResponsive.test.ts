/**
 * @file useResponsive.test.ts
 * @description 测试 useResponsive 响应式断点 composable
 * @vitest-environment jsdom
 */
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, h, defineComponent } from 'vue'
import {
  useResponsive,
  initResponsive,
  destroyResponsive,
  updateFontSize,
  DESIGN_WIDTH,
  BASE_FONT_SIZE,
  MIN_FONT_SIZE,
  MAX_FONT_SIZE,
} from '@/composables/useResponsive'

// ========== 挂载辅助 ==========

interface MountResult {
  result: ReturnType<typeof useResponsive>
  app: ReturnType<typeof createApp>
  el: HTMLDivElement
}

/** 挂载一个调用 useResponsive 的测试组件 */
function mountResponsive(): MountResult {
  const holder: { result: ReturnType<typeof useResponsive> | null } = { result: null }
  const TestComponent = defineComponent({
    setup() {
      holder.result = useResponsive()
      return () => h('div')
    },
  })
  const app = createApp(TestComponent)
  const el = document.createElement('div')
  app.mount(el)
  return { result: holder.result!, app, el }
}

/** 设置窗口尺寸并触发 resize 事件 */
function setWindowSize(width: number, height?: number) {
  Object.defineProperty(window, 'innerWidth', {
    writable: true,
    configurable: true,
    value: width,
  })
  if (height !== undefined) {
    Object.defineProperty(window, 'innerHeight', {
      writable: true,
      configurable: true,
      value: height,
    })
  }
  window.dispatchEvent(new Event('resize'))
}

describe('useResponsive', () => {
  const apps: ReturnType<typeof createApp>[] = []
  const originalWidth = window.innerWidth
  const originalHeight = window.innerHeight

  afterEach(() => {
    apps.forEach((a) => a.unmount())
    apps.length = 0
    // 恢复原始窗口尺寸
    Object.defineProperty(window, 'innerWidth', {
      writable: true,
      configurable: true,
      value: originalWidth,
    })
    Object.defineProperty(window, 'innerHeight', {
      writable: true,
      configurable: true,
      value: originalHeight,
    })
  })

  describe('初始断点检测', () => {
    it('应返回 isMobile / isTablet / isDesktop / isWide 四个标志位', () => {
      setWindowSize(1024)
      const { result, app } = mountResponsive()
      apps.push(app)

      expect(result.isMobile).toBeDefined()
      expect(result.isTablet).toBeDefined()
      expect(result.isDesktop).toBeDefined()
      expect(result.isWide).toBeDefined()
    })

    it('应返回 screenWidth / screenHeight / device', () => {
      setWindowSize(1024, 768)
      const { result, app } = mountResponsive()
      apps.push(app)

      expect(result.screenWidth).toBeDefined()
      expect(result.screenHeight).toBeDefined()
      expect(result.device).toBeDefined()
    })
  })

  describe('移动端 (< 768px)', () => {
    it('isMobile 应为 true', () => {
      setWindowSize(375)
      const { result, app } = mountResponsive()
      apps.push(app)

      expect(result.isMobile.value).toBe(true)
      expect(result.isTablet.value).toBe(false)
      expect(result.isDesktop.value).toBe(false)
      expect(result.isWide.value).toBe(false)
      expect(result.device.value).toBe('mobile')
    })
  })

  describe('平板 (768-1200px)', () => {
    it('isTablet 应为 true', () => {
      setWindowSize(768)
      const { result, app } = mountResponsive()
      apps.push(app)

      expect(result.isMobile.value).toBe(false)
      expect(result.isTablet.value).toBe(true)
      expect(result.isDesktop.value).toBe(false)
      expect(result.isWide.value).toBe(false)
      expect(result.device.value).toBe('tablet')
    })

    it('1024px 应属于平板', () => {
      setWindowSize(1024)
      const { result, app } = mountResponsive()
      apps.push(app)

      expect(result.isTablet.value).toBe(true)
      expect(result.device.value).toBe('tablet')
    })
  })

  describe('桌面端 (1200-1920px)', () => {
    it('isDesktop 应为 true', () => {
      setWindowSize(1200)
      const { result, app } = mountResponsive()
      apps.push(app)

      expect(result.isMobile.value).toBe(false)
      expect(result.isTablet.value).toBe(false)
      expect(result.isDesktop.value).toBe(true)
      expect(result.isWide.value).toBe(false)
      expect(result.device.value).toBe('desktop')
    })

    it('1440px 应属于桌面端', () => {
      setWindowSize(1440)
      const { result, app } = mountResponsive()
      apps.push(app)

      expect(result.isDesktop.value).toBe(true)
      expect(result.device.value).toBe('desktop')
    })
  })

  describe('大屏 (≥ 1920px)', () => {
    it('isWide 应为 true', () => {
      setWindowSize(1920)
      const { result, app } = mountResponsive()
      apps.push(app)

      expect(result.isMobile.value).toBe(false)
      expect(result.isTablet.value).toBe(false)
      expect(result.isDesktop.value).toBe(false)
      expect(result.isWide.value).toBe(true)
      expect(result.device.value).toBe('wide')
    })
  })

  describe('resize 事件响应', () => {
    it('窗口缩放应触发断点标志位更新', () => {
      setWindowSize(1024)
      const { result, app } = mountResponsive()
      apps.push(app)

      // 初始为平板
      expect(result.isTablet.value).toBe(true)

      // 缩小到移动端
      setWindowSize(375)
      expect(result.isMobile.value).toBe(true)
      expect(result.isTablet.value).toBe(false)

      // 放大到桌面端
      setWindowSize(1440)
      expect(result.isDesktop.value).toBe(true)
      expect(result.isMobile.value).toBe(false)
    })

    it('screenWidth 应随 resize 更新', () => {
      setWindowSize(500)
      const { result, app } = mountResponsive()
      apps.push(app)

      expect(result.screenWidth.value).toBe(500)

      setWindowSize(1500)
      expect(result.screenWidth.value).toBe(1500)
    })

    it('screenHeight 应随 resize 更新', () => {
      setWindowSize(1024, 600)
      const { result, app } = mountResponsive()
      apps.push(app)

      expect(result.screenHeight.value).toBe(600)

      setWindowSize(1024, 900)
      expect(result.screenHeight.value).toBe(900)
    })
  })

  describe('组件卸载', () => {
    it('卸载后应移除 resize 监听器', () => {
      const removeSpy = vi.spyOn(window, 'removeEventListener')
      const { app } = mountResponsive()

      app.unmount()

      // 应调用 removeEventListener 移除 resize
      const resizeCalls = removeSpy.mock.calls.filter((c) => c[0] === 'resize')
      expect(resizeCalls.length).toBeGreaterThan(0)

      removeSpy.mockRestore()
    })
  })

  // ===== P2-7: rem 自适应（initResponsive / updateFontSize） =====
  describe('rem 自适应 (initResponsive)', () => {
    // 保存 document.documentElement.clientWidth 原始描述符，用于测试后还原
    const originalClientWidthDesc = Object.getOwnPropertyDescriptor(
      document.documentElement,
      'clientWidth',
    )

    /** 设置 document.documentElement.clientWidth 并触发 font-size 重算 */
    function setClientWidth(width: number) {
      Object.defineProperty(document.documentElement, 'clientWidth', {
        configurable: true,
        writable: true,
        value: width,
      })
    }

    afterEach(() => {
      // 清理全局 rem 监听器，避免影响后续断点检测测试
      destroyResponsive()
      // 还原 clientWidth：有原始描述符则恢复，否则删除自定义 own 属性以恢复 jsdom 原型 getter
      if (originalClientWidthDesc) {
        Object.defineProperty(document.documentElement, 'clientWidth', originalClientWidthDesc)
      } else {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        delete (document.documentElement as any).clientWidth
      }
      document.documentElement.style.fontSize = ''
    })

    it('应导出正确的基准常量', () => {
      expect(DESIGN_WIDTH).toBe(1920)
      expect(BASE_FONT_SIZE).toBe(16)
      expect(MIN_FONT_SIZE).toBe(12)
      expect(MAX_FONT_SIZE).toBe(24)
    })

    it('1920px 视口应计算为 16px font-size', () => {
      setClientWidth(1920)
      const { fontSize } = initResponsive()

      expect(fontSize.value).toBe(16)
      expect(document.documentElement.style.fontSize).toBe('16px')
    })

    it('1366px 视口应被最小值 12px 限制', () => {
      setClientWidth(1366)
      initResponsive()

      // (1366 / 1920) * 16 = 11.383... → 限制为 MIN_FONT_SIZE (12)
      expect(document.documentElement.style.fontSize).toBe('12px')
    })

    it('小于最小阈值的视口应被限制为 12px', () => {
      setClientWidth(800)
      initResponsive()

      // (800 / 1920) * 16 = 6.666... → 限制为 12
      expect(document.documentElement.style.fontSize).toBe('12px')
    })

    it('2560px 视口应计算为约 21.33px（不受最小/最大限制）', () => {
      setClientWidth(2560)
      const { fontSize } = initResponsive()

      // (2560 / 1920) * 16 = 21.333...
      expect(fontSize.value).toBeCloseTo(21.333, 1)
      expect(document.documentElement.style.fontSize).toContain('21.33')
    })

    it('2880px 视口应恰好达到最大值 24px', () => {
      setClientWidth(2880)
      initResponsive()

      // (2880 / 1920) * 16 = 24
      expect(document.documentElement.style.fontSize).toBe('24px')
    })

    it('超过最大阈值的视口应被最大值 24px 限制', () => {
      setClientWidth(3000)
      initResponsive()

      // (3000 / 1920) * 16 = 25 → 限制为 MAX_FONT_SIZE (24)
      expect(document.documentElement.style.fontSize).toBe('24px')
    })

    it('updateFontSize 应直接更新 html style 与 fontSize ref', () => {
      setClientWidth(1920)
      updateFontSize()
      expect(document.documentElement.style.fontSize).toBe('16px')

      setClientWidth(3000)
      updateFontSize()
      expect(document.documentElement.style.fontSize).toBe('24px')
    })

    it('initResponsive 应幂等：重复调用不叠加 resize 监听器', () => {
      setClientWidth(1920)
      const addSpy = vi.spyOn(window, 'addEventListener')

      initResponsive()
      const firstCount = addSpy.mock.calls.filter((c) => c[0] === 'resize').length

      initResponsive()
      const secondCount = addSpy.mock.calls.filter((c) => c[0] === 'resize').length

      // 第二次调用不应新增 resize 监听器
      expect(secondCount).toBe(firstCount)
      expect(firstCount).toBeGreaterThan(0)

      addSpy.mockRestore()
    })

    it('resize 事件应触发 font-size 重新计算', () => {
      setClientWidth(1920)
      initResponsive()
      expect(document.documentElement.style.fontSize).toBe('16px')

      // 模拟视口放大到 2560px
      setClientWidth(2560)
      window.dispatchEvent(new Event('resize'))
      expect(document.documentElement.style.fontSize).toContain('21.33')

      // 模拟视口缩小到 1366px
      setClientWidth(1366)
      window.dispatchEvent(new Event('resize'))
      expect(document.documentElement.style.fontSize).toBe('12px')
    })

    it('destroyResponsive 应移除监听器并还原 html style', () => {
      setClientWidth(1920)
      initResponsive()
      expect(document.documentElement.style.fontSize).toBe('16px')

      const removeSpy = vi.spyOn(window, 'removeEventListener')
      destroyResponsive()

      const resizeRemoves = removeSpy.mock.calls.filter((c) => c[0] === 'resize')
      expect(resizeRemoves.length).toBeGreaterThan(0)
      expect(document.documentElement.style.fontSize).toBe('')

      // 销毁后 resize 不应再触发 font-size 变化
      setClientWidth(3000)
      window.dispatchEvent(new Event('resize'))
      expect(document.documentElement.style.fontSize).toBe('')

      removeSpy.mockRestore()
    })

    it('useResponsive 应返回共享的 fontSize ref', () => {
      setClientWidth(1920)
      initResponsive()

      const { result, app } = mountResponsive()
      apps.push(app)

      expect(result.fontSize).toBeDefined()
      // 共享全局 fontSize（1920px → 16px）
      expect(result.fontSize.value).toBe(16)
    })
  })
})
