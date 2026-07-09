/**
 * @file useFormDraft composable 单元测试
 * @description 测试表单草稿自动保存 composable 的核心逻辑
 * @module composables/__tests__/useFormDraft
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { reactive } from 'vue'
import { useFormDraft, clearAllDrafts, DRAFT_KEY_PREFIX } from '../useFormDraft'

describe('useFormDraft', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    localStorage.clear()
  })

  it('save 应该将表单数据写入 localStorage', () => {
    const form = reactive({ name: 'Test', code: 'T001' })
    const { save, hasDraft } = useFormDraft(form, { key: 'test-draft' })

    save()

    expect(hasDraft.value).toBe(true)
    const raw = localStorage.getItem(`${DRAFT_KEY_PREFIX}test-draft`)
    expect(raw).not.toBeNull()
    const parsed = JSON.parse(raw!)
    expect(parsed.form.name).toBe('Test')
    expect(parsed.form.code).toBe('T001')
    expect(parsed.savedAt).toBeTypeOf('number')
  })

  it('restore 应该从 localStorage 恢复表单数据', () => {
    const form = reactive({ name: '', code: '' })
    const { restore } = useFormDraft(form, { key: 'test-draft' })

    // 先写入草稿
    const data = { form: { name: 'Restored', code: 'R001' }, savedAt: Date.now() }
    localStorage.setItem(`${DRAFT_KEY_PREFIX}test-draft`, JSON.stringify(data))

    const result = restore()

    expect(result).toBe(true)
    expect(form.name).toBe('Restored')
    expect(form.code).toBe('R001')
  })

  it('restore 过期草稿应该返回 false 并清除', () => {
    const form = reactive({ name: '' })
    const { restore } = useFormDraft(form, { key: 'test-draft', maxAge: 1000 })

    const expired = { form: { name: 'Old' }, savedAt: Date.now() - 2000 }
    localStorage.setItem(`${DRAFT_KEY_PREFIX}test-draft`, JSON.stringify(expired))

    const result = restore()

    expect(result).toBe(false)
    expect(form.name).toBe('')
    expect(localStorage.getItem(`${DRAFT_KEY_PREFIX}test-draft`)).toBeNull()
  })

  it('restore 无草稿时应该返回 false', () => {
    const form = reactive({ name: '' })
    const { restore } = useFormDraft(form, { key: 'no-draft' })

    const result = restore()

    expect(result).toBe(false)
  })

  it('clear 应该清除 localStorage 中的草稿', () => {
    const form = reactive({ name: 'Test' })
    const { save, clear, hasDraft } = useFormDraft(form, { key: 'test-draft' })

    save()
    expect(hasDraft.value).toBe(true)

    clear()

    expect(hasDraft.value).toBe(false)
    expect(localStorage.getItem(`${DRAFT_KEY_PREFIX}test-draft`)).toBeNull()
  })

  it('userId 隔离：不同用户的草稿 key 不同', () => {
    const form1 = reactive({ name: 'User1' })
    const form2 = reactive({ name: 'User2' })

    useFormDraft(form1, { key: 'project-init', userId: 1001 })
    useFormDraft(form2, { key: 'project-init', userId: 1002 })

    // 两个 key 不同
    expect(localStorage.getItem(`${DRAFT_KEY_PREFIX}1001-project-init`)).toBeNull()
    expect(localStorage.getItem(`${DRAFT_KEY_PREFIX}1002-project-init`)).toBeNull()
  })

  it('clearAllDrafts 应该清理指定用户的所有草稿', () => {
    localStorage.setItem(`${DRAFT_KEY_PREFIX}1001-draft-a`, JSON.stringify({ form: {}, savedAt: Date.now() }))
    localStorage.setItem(`${DRAFT_KEY_PREFIX}1001-draft-b`, JSON.stringify({ form: {}, savedAt: Date.now() }))
    localStorage.setItem(`${DRAFT_KEY_PREFIX}1002-draft-c`, JSON.stringify({ form: {}, savedAt: Date.now() }))

    clearAllDrafts(1001)

    expect(localStorage.getItem(`${DRAFT_KEY_PREFIX}1001-draft-a`)).toBeNull()
    expect(localStorage.getItem(`${DRAFT_KEY_PREFIX}1001-draft-b`)).toBeNull()
    expect(localStorage.getItem(`${DRAFT_KEY_PREFIX}1002-draft-c`)).not.toBeNull()
  })

  it('clearAllDrafts 无 userId 时清理所有草稿', () => {
    localStorage.setItem(`${DRAFT_KEY_PREFIX}1001-draft-a`, JSON.stringify({ form: {}, savedAt: Date.now() }))
    localStorage.setItem(`${DRAFT_KEY_PREFIX}other-draft`, JSON.stringify({ form: {}, savedAt: Date.now() }))
    localStorage.setItem('unrelated-key', 'keep')

    clearAllDrafts()

    expect(localStorage.getItem(`${DRAFT_KEY_PREFIX}1001-draft-a`)).toBeNull()
    expect(localStorage.getItem(`${DRAFT_KEY_PREFIX}other-draft`)).toBeNull()
    expect(localStorage.getItem('unrelated-key')).toBe('keep')
  })

  it('表单变化时应该触发防抖保存', () => {
    const form = reactive({ name: '' })
    const { hasDraft } = useFormDraft(form, { key: 'test-draft', debounce: 1000 })

    form.name = 'Changed'
    // 防抖时间内不应保存
    vi.advanceTimersByTime(500)
    expect(hasDraft.value).toBe(false)

    // 超过防抖时间后应保存
    vi.advanceTimersByTime(600)
    expect(hasDraft.value).toBe(true)
    const raw = localStorage.getItem(`${DRAFT_KEY_PREFIX}test-draft`)
    expect(raw).not.toBeNull()
    expect(JSON.parse(raw!).form.name).toBe('Changed')
  })
})
