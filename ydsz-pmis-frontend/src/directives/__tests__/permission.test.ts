import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// 直接测试 v-permission 指令中的 check 逻辑
// 通过 setupPermissionDirective 注入到 mock app

const mockUserStore = {
  permissions: [] as string[],
}

vi.mock('@/store/modules/user', () => ({
  useUserStore: () => mockUserStore,
}))

import { setupPermissionDirective } from '@/directives/permission'

describe('v-permission 指令', () => {
  let app: any
  let directive: any

  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    mockUserStore.permissions = []
    app = {
      directive: vi.fn((name: string, d: any) => {
        if (name === 'permission') directive = d
      }),
    }
    setupPermissionDirective(app)
  })

  it('注册 permission 指令', () => {
    expect(app.directive).toHaveBeenCalledWith('permission', expect.anything())
    expect(directive).toBeDefined()
  })

  it('同时挂载 mounted 和 updated 钩子（响应式更新）', () => {
    expect(directive.mounted).toBeTypeOf('function')
    expect(directive.updated).toBeTypeOf('function')
  })

  it('空 value 不应改变元素状态', () => {
    const el = document.createElement('div')
    el.style.display = 'block'
    expect(() => directive.mounted(el, { value: undefined, modifiers: {} })).not.toThrow()
    // display 保持不变
    expect(el.style.display).toBe('block')
  })

  describe('默认模式：display:none 隐藏（非破坏性）', () => {
    it('单权限字符串匹配时保留元素显示', () => {
      mockUserStore.permissions = ['auth:user:create']
      const el = document.createElement('div')
      el.setAttribute('data-test', 'keep')
      document.body.appendChild(el)
      directive.mounted(el, { value: 'auth:user:create', modifiers: {} })
      // 元素仍在 DOM 中且 display 不为 none
      expect(document.querySelector('[data-test="keep"]')).not.toBeNull()
      expect(el.style.display).not.toBe('none')
      document.body.removeChild(el)
    })

    it('单权限字符串不匹配时隐藏元素（display:none 而非 removeChild）', () => {
      mockUserStore.permissions = ['auth:user:list']
      const el = document.createElement('div')
      el.setAttribute('data-test', 'hide-me')
      document.body.appendChild(el)
      directive.mounted(el, { value: 'auth:user:create', modifiers: {} })
      // 元素仍在 DOM 中（非破坏性），但 display 为 none
      expect(document.querySelector('[data-test="hide-me"]')).not.toBeNull()
      expect(el.style.display).toBe('none')
      document.body.removeChild(el)
    })

    it('数组权限 OR 模式 (默认): 任一匹配即保留', () => {
      mockUserStore.permissions = ['auth:user:list']
      const el = document.createElement('div')
      el.setAttribute('data-test', 'or-keep')
      document.body.appendChild(el)
      directive.mounted(el, {
        value: ['auth:user:create', 'auth:user:list'],
        modifiers: {},
      })
      expect(el.style.display).not.toBe('none')
      document.body.removeChild(el)
    })

    it('数组权限 AND 模式 (modifier .all): 全部匹配才保留', () => {
      mockUserStore.permissions = ['auth:user:create']
      const el = document.createElement('div')
      el.setAttribute('data-test', 'and-hide')
      document.body.appendChild(el)
      directive.mounted(el, {
        value: ['auth:user:create', 'auth:user:update'],
        modifiers: { all: true },
      })
      expect(el.style.display).toBe('none')
      document.body.removeChild(el)
    })

    it('数组权限 AND 模式: 全部匹配时保留显示', () => {
      mockUserStore.permissions = ['auth:user:create', 'auth:user:update']
      const el = document.createElement('div')
      el.setAttribute('data-test', 'and-keep')
      document.body.appendChild(el)
      directive.mounted(el, {
        value: ['auth:user:create', 'auth:user:update'],
        modifiers: { all: true },
      })
      expect(el.style.display).not.toBe('none')
      document.body.removeChild(el)
    })

    it('超级权限 *:*:* 始终放行', () => {
      mockUserStore.permissions = ['*:*:*']
      const el = document.createElement('div')
      el.setAttribute('data-test', 'super-keep')
      document.body.appendChild(el)
      directive.mounted(el, { value: 'any:random:perm', modifiers: {} })
      expect(el.style.display).not.toBe('none')
      document.body.removeChild(el)
    })

    it('数组权限 OR 模式: 无匹配则隐藏', () => {
      mockUserStore.permissions = ['auth:user:list']
      const el = document.createElement('div')
      el.setAttribute('data-test', 'or-hide')
      document.body.appendChild(el)
      directive.mounted(el, {
        value: ['auth:user:create', 'auth:user:delete'],
        modifiers: {},
      })
      expect(el.style.display).toBe('none')
      document.body.removeChild(el)
    })

    it('隐藏时保存原始 display 值，恢复时还原（可恢复性）', () => {
      mockUserStore.permissions = []
      const el = document.createElement('div')
      el.style.display = 'inline-block'
      document.body.appendChild(el)
      // 第一次：无权限 -> 隐藏
      directive.mounted(el, { value: 'auth:user:create', modifiers: {} })
      expect(el.style.display).toBe('none')
      // 第二次：授权 -> 恢复原始 display
      mockUserStore.permissions = ['auth:user:create']
      directive.updated(el, { value: 'auth:user:create', modifiers: {} })
      expect(el.style.display).toBe('inline-block')
      document.body.removeChild(el)
    })
  })

  describe('.disabled 修饰符：禁用态降级（而非隐藏）', () => {
    it('无权限时元素 disabled + aria-disabled + class + title + 视觉降级', () => {
      mockUserStore.permissions = []
      const el = document.createElement('button')
      el.setAttribute('data-test', 'disabled-btn')
      document.body.appendChild(el)
      directive.mounted(el, {
        value: 'auth:user:delete',
        modifiers: { disabled: true },
      })
      // 元素仍可见（display 不为 none）
      expect(el.style.display).not.toBe('none')
      // disabled 属性已设置
      expect(el.hasAttribute('disabled')).toBe(true)
      expect(el.getAttribute('aria-disabled')).toBe('true')
      // 视觉降级
      expect(el.classList.contains('perm-disabled')).toBe(true)
      expect(el.style.pointerEvents).toBe('none')
      expect(el.style.opacity).toBe('0.5')
      expect(el.style.cursor).toBe('not-allowed')
      // title 提示
      expect(el.getAttribute('title')).toBe('无权限执行此操作')
      document.body.removeChild(el)
    })

    it('有权限时元素正常显示（无 disabled/class/title 降级）', () => {
      mockUserStore.permissions = ['auth:user:delete']
      const el = document.createElement('button')
      el.setAttribute('data-test', 'enabled-btn')
      document.body.appendChild(el)
      directive.mounted(el, {
        value: 'auth:user:delete',
        modifiers: { disabled: true },
      })
      expect(el.hasAttribute('disabled')).toBe(false)
      expect(el.classList.contains('perm-disabled')).toBe(false)
      expect(el.style.pointerEvents).not.toBe('none')
      expect(el.style.opacity).not.toBe('0.5')
      document.body.removeChild(el)
    })

    it('恢复时还原原始 disabled 状态与 title', () => {
      mockUserStore.permissions = []
      const el = document.createElement('button')
      // 原本就是 disabled 的按钮
      el.setAttribute('disabled', 'disabled')
      el.setAttribute('title', '原始提示')
      document.body.appendChild(el)
      // 第一次：无权限 -> 应用降级
      directive.mounted(el, {
        value: 'auth:user:delete',
        modifiers: { disabled: true },
      })
      expect(el.getAttribute('title')).toBe('无权限执行此操作')
      // 第二次：授权 -> 恢复原始 disabled 和 title
      mockUserStore.permissions = ['auth:user:delete']
      directive.updated(el, {
        value: 'auth:user:delete',
        modifiers: { disabled: true },
      })
      expect(el.hasAttribute('disabled')).toBe(true)
      expect(el.getAttribute('title')).toBe('原始提示')
      document.body.removeChild(el)
    })

    it('.disabled 修饰符对原生 div 也生效（aria-disabled + class）', () => {
      mockUserStore.permissions = []
      const el = document.createElement('div')
      el.setAttribute('data-test', 'disabled-div')
      document.body.appendChild(el)
      directive.mounted(el, {
        value: 'auth:user:delete',
        modifiers: { disabled: true },
      })
      // div 也支持 disabled 属性 + aria-disabled
      expect(el.hasAttribute('disabled')).toBe(true)
      expect(el.getAttribute('aria-disabled')).toBe('true')
      expect(el.classList.contains('perm-disabled')).toBe(true)
      document.body.removeChild(el)
    })
  })

  describe('updated 钩子：响应式权限更新', () => {
    it('权限从无到有：元素从隐藏恢复显示', () => {
      mockUserStore.permissions = []
      const el = document.createElement('button')
      el.setAttribute('data-test', 'reactive-show')
      document.body.appendChild(el)
      // 初始无权限 -> 隐藏
      directive.mounted(el, { value: 'auth:user:create', modifiers: {} })
      expect(el.style.display).toBe('none')
      // 权限更新 -> 恢复显示
      mockUserStore.permissions = ['auth:user:create']
      directive.updated(el, { value: 'auth:user:create', modifiers: {} })
      expect(el.style.display).not.toBe('none')
      document.body.removeChild(el)
    })

    it('权限从有到无：元素从显示变为隐藏', () => {
      mockUserStore.permissions = ['auth:user:create']
      const el = document.createElement('button')
      el.setAttribute('data-test', 'reactive-hide')
      document.body.appendChild(el)
      // 初始有权限 -> 显示
      directive.mounted(el, { value: 'auth:user:create', modifiers: {} })
      expect(el.style.display).not.toBe('none')
      // 权限被收回 -> 隐藏
      mockUserStore.permissions = []
      directive.updated(el, { value: 'auth:user:create', modifiers: {} })
      expect(el.style.display).toBe('none')
      document.body.removeChild(el)
    })

    it('权限动态切换：.disabled 修饰符响应式更新', () => {
      mockUserStore.permissions = []
      const el = document.createElement('button')
      document.body.appendChild(el)
      // 无权限 -> 禁用态
      directive.mounted(el, {
        value: 'auth:user:delete',
        modifiers: { disabled: true },
      })
      expect(el.hasAttribute('disabled')).toBe(true)
      expect(el.classList.contains('perm-disabled')).toBe(true)
      // 授权 -> 恢复正常
      mockUserStore.permissions = ['auth:user:delete']
      directive.updated(el, {
        value: 'auth:user:delete',
        modifiers: { disabled: true },
      })
      expect(el.hasAttribute('disabled')).toBe(false)
      expect(el.classList.contains('perm-disabled')).toBe(false)
      document.body.removeChild(el)
    })

    it('updated 钩子多次调用幂等（不重复保存原始状态）', () => {
      mockUserStore.permissions = []
      const el = document.createElement('button')
      el.style.display = 'inline'
      document.body.appendChild(el)
      // 多次 updated 调用模拟权限频繁变化
      directive.mounted(el, { value: 'auth:user:create', modifiers: {} })
      expect(el.style.display).toBe('none')
      mockUserStore.permissions = ['auth:user:create']
      directive.updated(el, { value: 'auth:user:create', modifiers: {} })
      expect(el.style.display).toBe('inline')
      mockUserStore.permissions = []
      directive.updated(el, { value: 'auth:user:create', modifiers: {} })
      expect(el.style.display).toBe('none')
      mockUserStore.permissions = ['auth:user:create']
      directive.updated(el, { value: 'auth:user:create', modifiers: {} })
      // 原始 display 仍能正确还原（不被覆盖为 none）
      expect(el.style.display).toBe('inline')
      document.body.removeChild(el)
    })
  })
})
