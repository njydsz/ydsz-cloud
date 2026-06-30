import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '@/store/modules/user'

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

  it('空 value 不应删除元素', () => {
    // 当 value 为空时, 指令应直接 return, 不调用 el.parentNode.removeChild
    // 此时即使 el 没在 DOM 中, 也不会抛错 (因为没访问 parentNode)
    expect(() => directive.mounted(document.createElement('div'), { value: undefined, modifiers: {} })).not.toThrow()
  })

  it('单权限字符串匹配时保留元素', () => {
    mockUserStore.permissions = ['auth:user:create']
    const el = document.createElement('div')
    el.setAttribute('data-test', 'keep')
    document.body.appendChild(el)
    directive.mounted(el, { value: 'auth:user:create', modifiers: {} })
    expect(document.querySelector('[data-test="keep"]')).not.toBeNull()
    document.body.removeChild(el)
  })

  it('单权限字符串不匹配时移除元素', () => {
    mockUserStore.permissions = ['auth:user:list']
    const el = document.createElement('div')
    el.setAttribute('data-test', 'remove-me')
    document.body.appendChild(el)
    directive.mounted(el, { value: 'auth:user:create', modifiers: {} })
    expect(document.querySelector('[data-test="remove-me"]')).toBeNull()
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
    expect(document.querySelector('[data-test="or-keep"]')).not.toBeNull()
  })

  it('数组权限 AND 模式 (modifier .all): 全部匹配才保留', () => {
    mockUserStore.permissions = ['auth:user:create']
    const el = document.createElement('div')
    el.setAttribute('data-test', 'and-remove')
    document.body.appendChild(el)
    directive.mounted(el, {
      value: ['auth:user:create', 'auth:user:update'],
      modifiers: { all: true },
    })
    expect(document.querySelector('[data-test="and-remove"]')).toBeNull()
  })

  it('数组权限 AND 模式: 全部匹配时保留', () => {
    mockUserStore.permissions = ['auth:user:create', 'auth:user:update']
    const el = document.createElement('div')
    el.setAttribute('data-test', 'and-keep')
    document.body.appendChild(el)
    directive.mounted(el, {
      value: ['auth:user:create', 'auth:user:update'],
      modifiers: { all: true },
    })
    expect(document.querySelector('[data-test="and-keep"]')).not.toBeNull()
  })

  it('超级权限 *:*:* 始终放行', () => {
    mockUserStore.permissions = ['*:*:*']
    const el = document.createElement('div')
    el.setAttribute('data-test', 'super-keep')
    document.body.appendChild(el)
    directive.mounted(el, { value: 'any:random:perm', modifiers: {} })
    expect(document.querySelector('[data-test="super-keep"]')).not.toBeNull()
  })

  it('数组权限 OR 模式: 无匹配则移除', () => {
    mockUserStore.permissions = ['auth:user:list']
    const el = document.createElement('div')
    el.setAttribute('data-test', 'or-remove')
    document.body.appendChild(el)
    directive.mounted(el, {
      value: ['auth:user:create', 'auth:user:delete'],
      modifiers: {},
    })
    expect(document.querySelector('[data-test="or-remove"]')).toBeNull()
  })
})
