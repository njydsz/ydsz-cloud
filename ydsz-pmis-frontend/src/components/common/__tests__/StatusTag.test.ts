/**
 * @file StatusTag.test.ts
 * @description 测试 StatusTag 通用状态标签组件
 * @vitest-environment jsdom
 */
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, h, type Component } from 'vue'
import StatusTag from '../StatusTag.vue'

// ========== 挂载辅助 ==========

interface MountResult {
  el: HTMLDivElement
  app: ReturnType<typeof createApp>
}

/**
 * 挂载组件并注册 el-tag 桩
 * 桩将 type/size/effect 作为 data 属性暴露，便于断言
 */
function mount(Component: Component, props: Record<string, unknown> = {}): MountResult {
  const el = document.createElement('div')
  const app = createApp({ render: () => h(Component, props) })
  app.component('el-tag', {
    props: ['type', 'size', 'effect'],
    setup(p: Record<string, unknown>, { slots }: { slots: { default?: () => unknown[] } }) {
      return () =>
        h(
          'span',
          {
            class: 'el-tag-stub',
            'data-type': p.type,
            'data-size': p.size,
            'data-effect': p.effect,
          },
          slots.default?.(),
        )
    },
  })
  app.mount(el)
  return { el, app }
}

function getTag(el: HTMLElement): HTMLElement {
  return el.querySelector('.el-tag-stub') as HTMLElement
}

describe('StatusTag', () => {
  const results: MountResult[] = []

  afterEach(() => {
    results.forEach((r) => r.app.unmount())
    results.length = 0
  })

  describe('通过 map 映射渲染', () => {
    it('应根据 value 在 map 中查找并渲染对应 label 与 type', () => {
      const map = {
        ACTIVE: { label: '启用', type: 'success' },
        DISABLED: { label: '禁用', type: 'danger' },
      }
      const r = mount(StatusTag, { value: 'ACTIVE', map })
      results.push(r)
      const tag = getTag(r.el)
      expect(tag).not.toBeNull()
      expect(tag.textContent).toBe('启用')
      expect(tag.getAttribute('data-type')).toBe('success')
    })

    it('应支持不同 status 渲染不同 type', () => {
      const map = {
        PENDING: { label: '待审', type: 'warning' },
        APPROVED: { label: '已通过', type: 'success' },
        REJECTED: { label: '已驳回', type: 'danger' },
      }
      const r1 = mount(StatusTag, { value: 'PENDING', map })
      results.push(r1)
      expect(getTag(r1.el).getAttribute('data-type')).toBe('warning')

      const r2 = mount(StatusTag, { value: 'APPROVED', map })
      results.push(r2)
      expect(getTag(r2.el).getAttribute('data-type')).toBe('success')

      const r3 = mount(StatusTag, { value: 'REJECTED', map })
      results.push(r3)
      expect(getTag(r3.el).getAttribute('data-type')).toBe('danger')
    })

    it('value 不在 map 中时应使用 fallbackType', () => {
      const map = { ACTIVE: { label: '启用', type: 'success' } }
      const r = mount(StatusTag, { value: 'UNKNOWN', map, fallbackType: 'info' })
      results.push(r)
      const tag = getTag(r.el)
      expect(tag.textContent).toBe('UNKNOWN')
      expect(tag.getAttribute('data-type')).toBe('info')
    })

    it('value 不在 map 中且无 fallbackType 时默认 info', () => {
      const r = mount(StatusTag, { value: 'UNKNOWN' })
      results.push(r)
      const tag = getTag(r.el)
      expect(tag.getAttribute('data-type')).toBe('info')
    })
  })

  describe('直接传入 label / type', () => {
    it('应直接使用 label prop 渲染文本', () => {
      const r = mount(StatusTag, { label: '自定义文案' })
      results.push(r)
      const tag = getTag(r.el)
      expect(tag.textContent).toBe('自定义文案')
    })

    it('应直接使用 type prop 渲染类型', () => {
      const r = mount(StatusTag, { label: '测试', type: 'success' })
      results.push(r)
      const tag = getTag(r.el)
      expect(tag.getAttribute('data-type')).toBe('success')
    })

    it('传入 label 但不传 type 时默认 info', () => {
      const r = mount(StatusTag, { label: '测试' })
      results.push(r)
      const tag = getTag(r.el)
      expect(tag.getAttribute('data-type')).toBe('info')
    })
  })

  describe('边界值处理', () => {
    it('value 为 null 时应渲染 "-"', () => {
      const r = mount(StatusTag, { value: null })
      results.push(r)
      const tag = getTag(r.el)
      expect(tag.textContent).toBe('-')
    })

    it('value 为 undefined 时应渲染 "-"', () => {
      const r = mount(StatusTag, { value: undefined })
      results.push(r)
      const tag = getTag(r.el)
      expect(tag.textContent).toBe('-')
    })

    it('value 为数字时应转为字符串渲染', () => {
      const map = { '1': { label: '状态一', type: 'primary' } }
      const r = mount(StatusTag, { value: 1, map })
      results.push(r)
      const tag = getTag(r.el)
      expect(tag.textContent).toBe('状态一')
      expect(tag.getAttribute('data-type')).toBe('primary')
    })
  })

  describe('尺寸与效果', () => {
    it('应支持 size prop', () => {
      const r = mount(StatusTag, { label: '测试', size: 'small' })
      results.push(r)
      const tag = getTag(r.el)
      expect(tag.getAttribute('data-size')).toBe('small')
    })

    it('size 默认为 default', () => {
      const r = mount(StatusTag, { label: '测试' })
      results.push(r)
      const tag = getTag(r.el)
      expect(tag.getAttribute('data-size')).toBe('default')
    })

    it('plain=true 时 effect 应为 plain', () => {
      const r = mount(StatusTag, { label: '测试', plain: true })
      results.push(r)
      const tag = getTag(r.el)
      expect(tag.getAttribute('data-effect')).toBe('plain')
    })

    it('plain=false 时 effect 默认为 light', () => {
      const r = mount(StatusTag, { label: '测试' })
      results.push(r)
      const tag = getTag(r.el)
      expect(tag.getAttribute('data-effect')).toBe('light')
    })
  })
})
