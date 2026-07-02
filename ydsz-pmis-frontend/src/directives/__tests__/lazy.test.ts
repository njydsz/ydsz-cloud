/**
 * @file v-lazy 图片懒加载指令 单元测试
 * @description 验证指令注册、原生 loading=lazy 优先路径、IntersectionObserver 回退路径、
 *              占位图设置、src 变更重新观察、unmounted 清理 observer。
 * @module directives/__tests__/lazy
 */
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { setupLazyDirective } from '@/directives/lazy'

describe('v-lazy 图片懒加载指令', () => {
  let app: any
  let directive: any

  beforeEach(() => {
    vi.clearAllMocks()
    app = {
      directive: vi.fn((name: string, d: any) => {
        if (name === 'lazy') directive = d
      }),
    }
    setupLazyDirective(app)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('注册 lazy 指令', () => {
    expect(app.directive).toHaveBeenCalledWith('lazy', expect.anything())
    expect(directive).toBeDefined()
  })

  it('挂载 mounted / updated / unmounted 钩子', () => {
    expect(directive.mounted).toBeTypeOf('function')
    expect(directive.updated).toBeTypeOf('function')
    expect(directive.unmounted).toBeTypeOf('function')
  })

  describe('原生 loading=lazy 优先路径', () => {
    beforeEach(() => {
      // 模拟现代浏览器支持原生 loading="lazy"（'loading' in HTMLImageElement.prototype 为 true）
      function MockHTMLImageElement() {}
      MockHTMLImageElement.prototype = { loading: '' }
      vi.stubGlobal('HTMLImageElement', MockHTMLImageElement)
    })

    it('字符串 src 应设置 loading=lazy 并立即设置 src', () => {
      const el = document.createElement('img')
      const binding: any = { value: 'https://example.com/a.png' }
      directive.mounted(el, binding)

      expect(el.loading).toBe('lazy')
      expect(el.src).toContain('example.com/a.png')
    })

    it('对象配置 {src, placeholder} 应先设置占位图再设置真实 src', () => {
      // 原生支持时 placeholder 会被真实 src 覆盖
      const el = document.createElement('img')
      const binding: any = {
        value: { src: 'https://example.com/b.png', placeholder: '/loading.png' },
      }
      directive.mounted(el, binding)

      expect(el.loading).toBe('lazy')
      expect(el.src).toContain('example.com/b.png')
    })

    it('空 src 应跳过不报错', () => {
      const el = document.createElement('img')
      const binding: any = { value: '' }
      directive.mounted(el, binding)

      expect(el.src).toBe('')
    })

    it('updated src 未变化时不应重新加载', () => {
      const el = document.createElement('img')
      const binding: any = { value: 'https://example.com/c.png' }
      directive.mounted(el, binding)
      const firstSrc = el.src

      directive.updated(el, binding)
      expect(el.src).toBe(firstSrc)
    })

    it('updated src 变化时应重新加载', () => {
      const el = document.createElement('img')
      directive.mounted(el, { value: 'https://example.com/old.png' } as any)

      directive.updated(el, { value: 'https://example.com/new.png' } as any)
      expect(el.src).toContain('example.com/new.png')
    })
  })

  describe('IntersectionObserver 回退路径', () => {
    let observeMock: ReturnType<typeof vi.fn>
    let disconnectMock: ReturnType<typeof vi.fn>
    let observerInstance: any

    beforeEach(() => {
      // 移除原生 loading 支持（'loading' in HTMLImageElement.prototype 为 false）
      function MockHTMLImageElementNoLazy() {}
      MockHTMLImageElementNoLazy.prototype = {}
      vi.stubGlobal('HTMLImageElement', MockHTMLImageElementNoLazy)

      observeMock = vi.fn()
      disconnectMock = vi.fn()
      observerInstance = {
        observe: observeMock,
        disconnect: disconnectMock,
      }
      vi.stubGlobal('IntersectionObserver', vi.fn(() => observerInstance))
    })

    it('无原生支持时使用 IntersectionObserver 观察元素', () => {
      const el = document.createElement('img')
      const binding: any = { value: 'https://example.com/d.png' }
      directive.mounted(el, binding)

      expect(observeMock).toHaveBeenCalledWith(el)
    })

    it('设置 placeholder 时应先显示占位图', () => {
      const el = document.createElement('img')
      const binding: any = {
        value: { src: 'https://example.com/e.png', placeholder: '/placeholder.png' },
      }
      directive.mounted(el, binding)

      // 占位图在进入视口前已设置（src 属性包含 placeholder）
      expect(el.getAttribute('src')).toBe('/placeholder.png')
    })

    it('unmounted 应 disconnect observer', () => {
      const el = document.createElement('img')
      directive.mounted(el, { value: 'https://example.com/f.png' } as any)

      directive.unmounted(el)
      expect(disconnectMock).toHaveBeenCalled()
    })

    it('元素进入视口时应设置真实 src', () => {
      const el = document.createElement('img')
      const binding: any = { value: 'https://example.com/g.png' }
      directive.mounted(el, binding)

      // 模拟进入视口
      const callback = (IntersectionObserver as any).mock.calls[0][0]
      callback([{ isIntersecting: true, target: el }])

      expect(el.src).toContain('example.com/g.png')
      expect(disconnectMock).toHaveBeenCalled()
    })
  })
})
