/**
 * @file useFormDraft 表单草稿 单元测试 (P2-13)
 * @description 覆盖保存到 localStorage、恢复、无草稿返回 false、清除、过期丢弃、
 *   防抖保存等核心行为. 使用 fake timers 控制防抖延迟.
 * @module composables/__tests__/useFormDraft
 */
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { ref } from 'vue'
import { useFormDraft } from '@/composables/useFormDraft'

describe('useFormDraft 表单草稿', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('save 将草稿写入 localStorage', () => {
    const form = ref({ name: 'test', value: 123 })
    const { save } = useFormDraft(form.value, { key: 'test-form' })
    save()
    const raw = localStorage.getItem('pmis-draft-test-form')
    expect(raw).toBeTruthy()
    const data = JSON.parse(raw!)
    expect(data.form.name).toBe('test')
    expect(data.form.value).toBe(123)
  })

  it('restore 从 localStorage 恢复草稿', () => {
    const form = ref({ name: '', value: 0 })
    localStorage.setItem(
      'pmis-draft-test-form',
      JSON.stringify({
        form: { name: 'restored', value: 456 },
        savedAt: Date.now(),
      }),
    )
    const { restore } = useFormDraft(form.value, { key: 'test-form' })
    expect(restore()).toBe(true)
    expect(form.value.name).toBe('restored')
    expect(form.value.value).toBe(456)
  })

  it('无草稿时 restore 返回 false', () => {
    const form = ref({ name: '' })
    const { restore } = useFormDraft(form.value, { key: 'no-draft' })
    expect(restore()).toBe(false)
  })

  it('clear 清除草稿', () => {
    const form = ref({ name: 'test' })
    const { save, clear } = useFormDraft(form.value, { key: 'test-clear' })
    save()
    expect(localStorage.getItem('pmis-draft-test-clear')).toBeTruthy()
    clear()
    expect(localStorage.getItem('pmis-draft-test-clear')).toBeNull()
  })

  it('过期草稿 restore 返回 false 并清除', () => {
    const form = ref({ name: '' })
    localStorage.setItem(
      'pmis-draft-test-expire',
      JSON.stringify({
        form: { name: 'old' },
        savedAt: Date.now() - 100000, // 很久以前
      }),
    )
    const { restore } = useFormDraft(form.value, {
      key: 'test-expire',
      maxAge: 1000,
    })
    expect(restore()).toBe(false)
    expect(form.value.name).toBe('')
  })

  it('表单变化时防抖保存', () => {
    const form = ref({ name: '' })
    const { debouncedSave } = useFormDraft(form.value, {
      key: 'test-debounce',
      debounce: 100,
    })
    form.value.name = 'changed'
    debouncedSave()
    // 防抖未到期, 尚未写入
    expect(localStorage.getItem('pmis-draft-test-debounce')).toBeNull()
    // 推进假时间, 触发保存
    vi.advanceTimersByTime(100)
    expect(localStorage.getItem('pmis-draft-test-debounce')).toBeTruthy()
  })
})
