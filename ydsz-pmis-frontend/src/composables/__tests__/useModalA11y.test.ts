/**
 * @file useModalA11y.test.ts
 * @description 测试 useModalA11y 模态框无障碍访问增强 composable
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp, h, defineComponent, ref, nextTick } from 'vue'
import { useModalA11y } from '@/composables/useModalA11y'

// ========== 挂载辅助 ==========

interface MountResult {
  result: ReturnType<typeof useModalA11y>
  app: ReturnType<typeof createApp>
  visible: ReturnType<typeof ref<boolean>>
}

/**
 * 挂载一个调用 useModalA11y 的测试组件
 * @param visible 初始可见性
 * @param options 传给 useModalA11y 的配置
 */
function mountA11y(
  visible = ref(false),
  options: Parameters<typeof useModalA11y>[1] = {},
): MountResult {
  const holder: { result: ReturnType<typeof useModalA11y> | null } = { result: null }
  const TestComponent = defineComponent({
    setup() {
      holder.result = useModalA11y(visible, options)
      return () => h('div')
    },
  })
  const app = createApp(TestComponent)
  const el = document.createElement('div')
  app.mount(el)
  return { result: holder.result!, app, visible }
}

describe('useModalA11y', () => {
  const apps: ReturnType<typeof createApp>[] = []

  beforeEach(() => {
    // 确保 body 干净，避免上次测试残留
    document.body.innerHTML = ''
  })

  afterEach(() => {
    apps.forEach((a) => a.unmount())
    apps.length = 0
    document.body.innerHTML = ''
  })

  describe('返回值', () => {
    it('应返回 restoreFocus 方法', () => {
      const { result } = mountA11y()
      expect(typeof result.restoreFocus).toBe('function')
    })
  })

  describe('焦点恢复', () => {
    it('打开对话框时记录当前 activeElement', async () => {
      const trigger = document.createElement('button')
      trigger.textContent = '打开'
      document.body.appendChild(trigger)
      trigger.focus()
      expect(document.activeElement).toBe(trigger)

      const { visible } = mountA11y()
      visible.value = true
      await nextTick()

      // 关闭后焦点应恢复到触发器
      visible.value = false
      await nextTick()

      expect(document.activeElement).toBe(trigger)
    })

    it('关闭时恢复焦点到显式传入的 triggerEl', async () => {
      const triggerRef = ref<HTMLElement | null>(null)
      const { visible } = mountA11y(ref(false), { triggerEl: triggerRef })

      // 模拟触发器按钮（绑定 ref）
      const trigger = document.createElement('button')
      trigger.textContent = '新增'
      document.body.appendChild(trigger)
      triggerRef.value = trigger

      visible.value = true
      await nextTick()

      visible.value = false
      await nextTick()

      expect(document.activeElement).toBe(trigger)
    })

    it('未传入 triggerEl 时恢复到打开前的 activeElement', async () => {
      const opener = document.createElement('button')
      opener.textContent = '编辑'
      document.body.appendChild(opener)
      opener.focus()
      expect(document.activeElement).toBe(opener)

      const { visible } = mountA11y()
      visible.value = true
      await nextTick()

      visible.value = false
      await nextTick()

      expect(document.activeElement).toBe(opener)
    })

    it('restoreFocus 手动调用应聚焦到 triggerEl', async () => {
      const triggerRef = ref<HTMLElement | null>(null)
      const { result } = mountA11y(ref(false), { triggerEl: triggerRef })

      const trigger = document.createElement('button')
      document.body.appendChild(trigger)
      triggerRef.value = trigger

      result.restoreFocus()

      expect(document.activeElement).toBe(trigger)
    })

    it('triggerEl 为空且无先前焦点时，restoreFocus 不抛错', () => {
      const { result } = mountA11y()
      expect(() => result.restoreFocus()).not.toThrow()
    })
  })

  describe('aria-modal 属性确保', () => {
    it('打开对话框后为缺失 aria-modal 的元素补全属性', async () => {
      // 使用自定义 selector 匹配未设置 aria-modal 的元素，验证防御性补全
      const modal = document.createElement('div')
      modal.className = 'custom-modal'
      document.body.appendChild(modal)

      const { visible } = mountA11y(ref(false), { selector: '.custom-modal' })
      visible.value = true
      // watch 回调内部 await nextTick，需等待两轮微任务确保 ensureAriaModal 执行完成
      await nextTick()
      await nextTick()

      expect(modal.getAttribute('aria-modal')).toBe('true')
    })

    it('已存在 aria-modal 时不被覆盖', async () => {
      const modal = document.createElement('div')
      modal.className = 'custom-modal'
      modal.setAttribute('aria-modal', 'true')
      document.body.appendChild(modal)

      const { visible } = mountA11y(ref(false), { selector: '.custom-modal' })
      visible.value = true
      await nextTick()
      await nextTick()

      expect(modal.getAttribute('aria-modal')).toBe('true')
    })

    it('自定义 selector 应正确匹配并补全', async () => {
      const modal = document.createElement('div')
      modal.setAttribute('role', 'dialog')
      document.body.appendChild(modal)

      const { visible } = mountA11y(ref(false), { selector: '[role="dialog"]' })
      visible.value = true
      // watch 回调内部 await nextTick，需等待两轮微任务确保 ensureAriaModal 执行完成
      await nextTick()
      await nextTick()

      expect(modal.getAttribute('aria-modal')).toBe('true')
    })
  })

  describe('watch 行为', () => {
    it('visible 由 true 切到 false 时触发焦点恢复', async () => {
      const triggerRef = ref<HTMLElement | null>(null)
      const focusSpy = vi.fn()
      const { visible } = mountA11y(ref(false), { triggerEl: triggerRef })

      const trigger = document.createElement('button')
      trigger.focus = focusSpy
      triggerRef.value = trigger

      visible.value = true
      await nextTick()
      expect(focusSpy).not.toHaveBeenCalled()

      visible.value = false
      await nextTick()
      expect(focusSpy).toHaveBeenCalledTimes(1)
    })

    it('多次打开关闭应正确恢复焦点', async () => {
      const trigger = document.createElement('button')
      document.body.appendChild(trigger)
      trigger.focus()
      const focusSpy = vi.spyOn(trigger, 'focus')

      const { visible } = mountA11y()

      // 第一次打开关闭
      visible.value = true
      await nextTick()
      visible.value = false
      await nextTick()
      expect(focusSpy).toHaveBeenCalledTimes(1)

      // 第二次打开关闭
      visible.value = true
      await nextTick()
      visible.value = false
      await nextTick()
      expect(focusSpy).toHaveBeenCalledTimes(2)

      focusSpy.mockRestore()
    })
  })
})
