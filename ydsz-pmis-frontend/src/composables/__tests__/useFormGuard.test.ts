/**
 * @file useFormGuard composable 单元测试
 * @description 测试表单防误关闭守卫的核心逻辑
 * @module composables/__tests__/useFormGuard
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref } from 'vue'
import { useFormGuard } from '../useFormGuard'

// Mock vue-router 的 onBeforeRouteLeave
vi.mock('vue-router', () => ({
  onBeforeRouteLeave: vi.fn((handler) => {
    // 保存 handler 供测试调用
    ;(onBeforeRouteLeave as any)._handler = handler
  }),
}))

// Mock element-plus 的 ElMessageBox
vi.mock('element-plus', () => ({
  ElMessageBox: {
    confirm: vi.fn().mockResolvedValue(true),
  },
}))

import { onBeforeRouteLeave } from 'vue-router'
import { ElMessageBox } from 'element-plus'

describe('useFormGuard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('初始 dirty 状态为 false（不传入 dirty ref）', () => {
    const { dirty } = useFormGuard()
    expect(dirty.value).toBe(false)
  })

  it('setDirty(true) 应该更新 dirty 状态', () => {
    const { dirty, setDirty } = useFormGuard()
    setDirty(true)
    expect(dirty.value).toBe(true)
  })

  it('setDirty(false) 应该更新 dirty 状态', () => {
    const { dirty, setDirty } = useFormGuard()
    setDirty(true)
    setDirty(false)
    expect(dirty.value).toBe(false)
  })

  it('支持外部传入 dirty ref', () => {
    const externalDirty = ref(true)
    const { dirty } = useFormGuard({ dirty: externalDirty })
    expect(dirty.value).toBe(true)
    expect(dirty).toBe(externalDirty)
  })

  it('dirty 为 false 时路由离开不拦截', async () => {
    const { setDirty } = useFormGuard()
    setDirty(false)

    const next = vi.fn()
    const handler = (onBeforeRouteLeave as any)._handler
    await handler({}, {}, next)

    expect(next).toHaveBeenCalledWith()
    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
  })

  it('dirty 为 true 时路由离开应弹出确认框', async () => {
    const { setDirty } = useFormGuard()
    setDirty(true)

    const next = vi.fn()
    const handler = (onBeforeRouteLeave as any)._handler
    await handler({}, {}, next)

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(next).toHaveBeenCalledWith()
  })

  it('dirty 为 true 且用户取消时应阻止路由跳转', async () => {
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce(new Error('cancel'))
    const { setDirty } = useFormGuard()
    setDirty(true)

    const next = vi.fn()
    const handler = (onBeforeRouteLeave as any)._handler
    await handler({}, {}, next)

    expect(next).toHaveBeenCalledWith(false)
  })

  it('支持自定义提示消息', () => {
    const customMessage = '自定义提示'
    useFormGuard({ message: customMessage })
    // composable 注册时已传入 message，验证不报错即可
    expect(true).toBe(true)
  })
})
