/**
 * @file useFormGuard.test.ts
 * @description 测试 useFormGuard 表单防误关闭守卫 composable
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createApp, h, defineComponent, ref } from 'vue'

// ========== 用 vi.hoisted 创建 mock 函数与路由守卫回调持有者（确保 vi.mock 工厂可访问） ==========
const { guardHolder, mockConfirm } = vi.hoisted(() => ({
  guardHolder: {
    current: null as null | ((to: unknown, from: unknown, next: (v?: unknown) => void) => Promise<void> | void),
  },
  mockConfirm: vi.fn(),
}))

// ========== Mock 依赖 ==========

// 模拟 vue-router 的 onBeforeRouteLeave，捕获守卫回调以便测试中手动触发
vi.mock('vue-router', () => ({
  onBeforeRouteLeave: (cb: typeof guardHolder.current) => {
    guardHolder.current = cb
  },
}))

// 模拟 element-plus 的 ElMessageBox.confirm
vi.mock('element-plus', () => ({
  ElMessageBox: {
    confirm: mockConfirm,
  },
}))

// ========== 导入被测试模块 ==========
import { useFormGuard } from '@/composables/useFormGuard'

// ========== 测试辅助：在组件 setup 中执行 composable ==========
interface WithGuardResult {
  result: ReturnType<typeof useFormGuard>
  app: ReturnType<typeof createApp>
  el: HTMLDivElement
}

/** 挂载一个调用 useFormGuard 的测试组件，返回 composable 结果与 app 实例 */
function withGuard(options: Parameters<typeof useFormGuard>[0] = {}): WithGuardResult {
  const result = {} as ReturnType<typeof useFormGuard>
  const TestComponent = defineComponent({
    setup() {
      Object.assign(result, useFormGuard(options))
      return () => h('div')
    },
  })
  const app = createApp(TestComponent)
  const el = document.createElement('div')
  app.mount(el)
  return { result, app, el }
}

describe('useFormGuard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    guardHolder.current = null
  })

  it('dirty=false 时，路由切换不拦截', async () => {
    withGuard()
    expect(guardHolder.current).not.toBeNull()

    const next = vi.fn()
    await guardHolder.current!({}, {}, next)

    expect(mockConfirm).not.toHaveBeenCalled()
    expect(next).toHaveBeenCalled()
    expect(next).not.toHaveBeenCalledWith(false)
  })

  it('dirty=true 时，路由切换触发 confirm', async () => {
    const dirtyRef = ref(true)
    withGuard({ dirty: dirtyRef })

    const next = vi.fn()
    mockConfirm.mockResolvedValue('confirm')

    await guardHolder.current!({}, {}, next)

    expect(mockConfirm).toHaveBeenCalledTimes(1)
    expect(next).toHaveBeenCalled()
    expect(next).not.toHaveBeenCalledWith(false)
  })

  it('setDirty(true) 后路由切换触发 confirm', async () => {
    const { result } = withGuard()
    result.setDirty(true)

    const next = vi.fn()
    mockConfirm.mockResolvedValue('confirm')

    await guardHolder.current!({}, {}, next)

    expect(mockConfirm).toHaveBeenCalledTimes(1)
    expect(next).toHaveBeenCalled()
  })

  it('setDirty(false) 后路由切换不拦截', async () => {
    const { result } = withGuard()
    // 先标记为 dirty，再清除
    result.setDirty(true)
    result.setDirty(false)

    const next = vi.fn()
    await guardHolder.current!({}, {}, next)

    expect(mockConfirm).not.toHaveBeenCalled()
    expect(next).toHaveBeenCalled()
  })

  it('用户取消 confirm 时，路由切换被阻止（next(false)）', async () => {
    const { result } = withGuard()
    result.setDirty(true)

    const next = vi.fn()
    mockConfirm.mockRejectedValue(new Error('cancel'))

    await guardHolder.current!({}, {}, next)

    expect(mockConfirm).toHaveBeenCalledTimes(1)
    expect(next).toHaveBeenCalledWith(false)
  })

  it('自定义 message 应传递给 ElMessageBox.confirm', async () => {
    const customMessage = '自定义离开提示文案'
    const dirtyRef = ref(true)
    withGuard({ dirty: dirtyRef, message: customMessage })

    const next = vi.fn()
    mockConfirm.mockResolvedValue('confirm')

    await guardHolder.current!({}, {}, next)

    expect(mockConfirm).toHaveBeenCalledWith(
      customMessage,
      '提示',
      expect.objectContaining({
        confirmButtonText: '离开',
        cancelButtonText: '取消',
        type: 'warning',
      }),
    )
  })

  it('组件卸载后 beforeunload 监听器被移除', () => {
    const addSpy = vi.spyOn(window, 'addEventListener')
    const removeSpy = vi.spyOn(window, 'removeEventListener')

    const { app } = withGuard()

    // 挂载后应注册 beforeunload 监听
    expect(addSpy).toHaveBeenCalledWith('beforeunload', expect.any(Function))
    const beforeUnloadCalls = addSpy.mock.calls.filter((c) => c[0] === 'beforeunload')
    expect(beforeUnloadCalls).toHaveLength(1)
    const addedListener = beforeUnloadCalls[0]![1] as EventListener

    // 卸载后应移除同一个监听器
    app.unmount()

    expect(removeSpy).toHaveBeenCalledWith('beforeunload', addedListener)
    const removedBeforeUnload = removeSpy.mock.calls.filter((c) => c[0] === 'beforeunload')
    expect(removedBeforeUnload).toHaveLength(1)

    addSpy.mockRestore()
    removeSpy.mockRestore()
  })

  it('dirty=true 时 beforeunload 事件设置 returnValue', () => {
    const addSpy = vi.spyOn(window, 'addEventListener')
    const { app, result } = withGuard()
    result.setDirty(true)

    // 从 spy 中取出 beforeunload 监听器
    const listener = addSpy.mock.calls
      .filter((c) => c[0] === 'beforeunload')
      .pop()?.[1] as EventListener | undefined

    expect(listener).toBeDefined()

    // 模拟 beforeunload 事件对象
    const event = {
      preventDefault: vi.fn(),
      returnValue: '',
    } as unknown as BeforeUnloadEvent

    listener!.call(window, event)

    expect(event.preventDefault).toHaveBeenCalled()
    expect(event.returnValue).not.toBe('')

    app.unmount()
    addSpy.mockRestore()
  })

  it('dirty=false 时 beforeunload 不设置 returnValue', () => {
    const addSpy = vi.spyOn(window, 'addEventListener')
    const { app } = withGuard()
    // dirty 默认为 false

    const listener = addSpy.mock.calls
      .filter((c) => c[0] === 'beforeunload')
      .pop()?.[1] as EventListener | undefined

    expect(listener).toBeDefined()

    const event = {
      preventDefault: vi.fn(),
      returnValue: '',
    } as unknown as BeforeUnloadEvent

    listener!.call(window, event)

    expect(event.preventDefault).not.toHaveBeenCalled()
    expect(event.returnValue).toBe('')

    app.unmount()
    addSpy.mockRestore()
  })
})
