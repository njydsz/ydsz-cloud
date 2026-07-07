/**
 * @file EmptyState.test.ts
 * @description 测试 EmptyState 通用空状态组件
 * @vitest-environment jsdom
 */
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, h, type Component } from 'vue'

// Mock vue-i18n，避免组件 setup 中 useI18n 报错
vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

import EmptyState from '../EmptyState.vue'

// ========== 挂载辅助 ==========

interface MountResult {
  el: HTMLDivElement
  app: ReturnType<typeof createApp>
}

/**
 * 挂载组件，注册 el-icon / el-button 桩并支持插槽
 */
function mount(
  Component: Component,
  props: Record<string, unknown> = {},
  slots: Record<string, () => unknown> = {},
): MountResult {
  const el = document.createElement('div')
  const app = createApp({ render: () => h(Component, props, slots) })

  // 桩 el-icon：渲染为 span 容器
  app.component('el-icon', {
    setup(_: unknown, { slots }: { slots: { default?: () => unknown[] } }) {
      return () => h('span', { class: 'el-icon-stub' }, slots.default?.())
    },
  })
  // 桩 el-button：渲染为 button，透传 type
  app.component('el-button', {
    props: ['type'],
    setup(p: Record<string, unknown>, { slots, emit }: { slots: { default?: () => unknown[] }; emit: (e: string) => void }) {
      return () =>
        h(
          'button',
          { class: 'el-button-stub', 'data-type': p.type, onClick: () => emit('click') },
          slots.default?.(),
        )
    },
  })

  app.mount(el)
  return { el, app }
}

describe('EmptyState', () => {
  const results: MountResult[] = []

  afterEach(() => {
    results.forEach((r) => r.app.unmount())
    results.length = 0
  })

  describe('默认渲染', () => {
    it('默认 preset=list 时应渲染 title', () => {
      const r = mount(EmptyState)
      results.push(r)
      const title = r.el.querySelector('.empty-state__title')
      expect(title).not.toBeNull()
      // title 来自 i18n key（mock 后为 key 本身）
      expect(title!.textContent).toBeTruthy()
    })

    it('默认 preset=list 时应渲染 description', () => {
      const r = mount(EmptyState)
      results.push(r)
      const desc = r.el.querySelector('.empty-state__description')
      expect(desc).not.toBeNull()
      expect(desc!.textContent).toBeTruthy()
    })
  })

  describe('description prop', () => {
    it('传入 description 时应覆盖 preset 默认描述', () => {
      const r = mount(EmptyState, { description: '自定义空数据描述' })
      results.push(r)
      const desc = r.el.querySelector('.empty-state__description')
      expect(desc!.textContent).toBe('自定义空数据描述')
    })

    it('preset=custom 时应使用传入的 description', () => {
      const r = mount(EmptyState, {
        preset: 'custom',
        title: '自定义标题',
        description: '自定义描述内容',
      })
      results.push(r)
      const title = r.el.querySelector('.empty-state__title')
      const desc = r.el.querySelector('.empty-state__description')
      expect(title!.textContent).toBe('自定义标题')
      expect(desc!.textContent).toBe('自定义描述内容')
    })
  })

  describe('title prop', () => {
    it('传入 title 时应覆盖 preset 默认标题', () => {
      const r = mount(EmptyState, { title: '暂无项目数据' })
      results.push(r)
      const title = r.el.querySelector('.empty-state__title')
      expect(title!.textContent).toBe('暂无项目数据')
    })
  })

  describe('action 插槽', () => {
    it('应渲染 action 插槽内容', () => {
      const r = mount(EmptyState, {}, {
        action: () => h('button', { class: 'custom-action' }, '清除筛选'),
      })
      results.push(r)
      const actionContainer = r.el.querySelector('.empty-state__action')
      expect(actionContainer).not.toBeNull()
      const btn = actionContainer!.querySelector('.custom-action')
      expect(btn).not.toBeNull()
      expect(btn!.textContent).toBe('清除筛选')
    })
  })

  describe('extra 插槽', () => {
    it('应渲染 extra 插槽内容', () => {
      const r = mount(EmptyState, {}, {
        extra: () => h('div', { class: 'extra-tip' }, '需要帮助？'),
      })
      results.push(r)
      const extra = r.el.querySelector('.extra-tip')
      expect(extra).not.toBeNull()
      expect(extra!.textContent).toBe('需要帮助？')
    })
  })

  describe('actionText prop', () => {
    it('传入 actionText 时应渲染 CTA 按钮', () => {
      const r = mount(EmptyState, { actionText: '新建项目' })
      results.push(r)
      const btn = r.el.querySelector('.el-button-stub')
      expect(btn).not.toBeNull()
      expect(btn!.textContent).toBe('新建项目')
    })

    it('不传 actionText 且无 action 插槽时不渲染 action 区域', () => {
      const r = mount(EmptyState)
      results.push(r)
      const action = r.el.querySelector('.empty-state__action')
      expect(action).toBeNull()
    })
  })

  describe('imageUrl prop', () => {
    it('传入 imageUrl 时应渲染图片', () => {
      const r = mount(EmptyState, { imageUrl: '/empty.png' })
      results.push(r)
      const img = r.el.querySelector('.empty-state__image')
      expect(img).not.toBeNull()
      expect(img!.getAttribute('src')).toBe('/empty.png')
    })
  })

  describe('blockHeight prop', () => {
    it('传入 blockHeight 时应设置容器高度', () => {
      const r = mount(EmptyState, { blockHeight: 300 })
      results.push(r)
      const container = r.el.querySelector('.empty-state') as HTMLElement
      expect(container.style.height).toBe('300px')
    })

    it('不传 blockHeight 时不设置高度', () => {
      const r = mount(EmptyState)
      results.push(r)
      const container = r.el.querySelector('.empty-state') as HTMLElement
      expect(container.style.height).toBe('')
    })
  })
})
