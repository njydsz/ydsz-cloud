/**
 * @file 表单草稿自动保存 composable (P2-13 表单草稿/自动保存)
 * @description 提供表单数据的防抖自动保存到 localStorage, 支持恢复、清除与过期判断.
 *              典型用于长表单(项目立项/合同录入)防止用户误关页面导致数据丢失.
 * @module composables/useFormDraft
 *
 * 用法:
 *   const form = reactive({ name: '', code: '' })
 *   const { hasDraft, restore, clear, save } = useFormDraft(form, { key: 'project-init' })
 *
 *   // 进入页面时提示恢复草稿
 *   if (hasDraft.value) {
 *     await ElMessageBox.confirm('检测到未提交的草稿, 是否恢复?')
 *     restore()
 *   }
 *
 *   // 提交成功后清除草稿
 *   async function onSubmit() {
 *     await api.save(form)
 *     clear()
 *   }
 */
import { ref, watch, onMounted, getCurrentInstance } from 'vue'

interface DraftOptions {
  /** 草稿唯一标识, 最终存储 key 为 `pmis-draft-${userId}-${key}` (无 userId 时退化为 `pmis-draft-${key}`) */
  key: string
  /** 防抖延迟(ms), 默认 2000 */
  debounce?: number
  /** 草稿最大有效期(ms), 默认 24h, 超期自动丢弃 */
  maxAge?: number
  /** 当前用户 ID, 用于按用户隔离草稿, 防止公共电脑场景下跨用户数据泄漏; 传 0/空串时不做用户隔离 */
  userId?: string | number | null
}

/** 草稿存储 key 前缀, 登出时按此前缀批量清理 */
export const DRAFT_KEY_PREFIX = 'pmis-draft-'

/**
 * 清理当前用户的所有草稿 (登出/切换账号时调用).
 * 仅清理匹配 `pmis-draft-${userId}-` 前缀的 key, 不影响其他用户.
 * @param userId 当前用户 ID; 不传则清理所有 `pmis-draft-` 前缀的草稿
 */
export function clearAllDrafts(userId?: string | number | null): void {
  try {
    const prefix = userId ? `${DRAFT_KEY_PREFIX}${userId}-` : DRAFT_KEY_PREFIX
    const keysToRemove: string[] = []
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i)
      if (k && k.startsWith(prefix)) keysToRemove.push(k)
    }
    keysToRemove.forEach((k) => localStorage.removeItem(k))
  } catch {
    // localStorage 不可用时静默降级
  }
}

/**
 * 表单草稿自动保存 composable.
 * 防抖保存到 localStorage, 支持恢复和清除.
 *
 * @param formData 表单数据对象(reactive 或 ref.value), 会就地写入恢复的数据
 * @param options 草稿配置
 */
export function useFormDraft<T extends Record<string, unknown>>(
  formData: T,
  options: DraftOptions,
) {
  const { key, debounce = 2000, maxAge = 86400000, userId } = options
  const hasDraft = ref(false)
  const lastSavedAt = ref<Date | null>(null)
  let timer: ReturnType<typeof setTimeout> | null = null

  // 按用户隔离: 有 userId 时 key 形如 `pmis-draft-1001-project-init`, 防止跨用户泄漏
  const storageKey = userId
    ? `${DRAFT_KEY_PREFIX}${userId}-${key}`
    : `${DRAFT_KEY_PREFIX}${key}`

  const save = () => {
    try {
      const data = {
        form: { ...formData },
        savedAt: Date.now(),
      }
      localStorage.setItem(storageKey, JSON.stringify(data))
      hasDraft.value = true
      lastSavedAt.value = new Date()
    } catch {
      // localStorage 可能已满或不可用, 静默降级
    }
  }

  const debouncedSave = () => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(save, debounce)
  }

  const restore = (): boolean => {
    try {
      const raw = localStorage.getItem(storageKey)
      if (!raw) return false
      const data = JSON.parse(raw)
      // 校验是否过期
      if (Date.now() - data.savedAt > maxAge) {
        localStorage.removeItem(storageKey)
        return false
      }
      Object.assign(formData, data.form)
      hasDraft.value = true
      lastSavedAt.value = new Date(data.savedAt)
      return true
    } catch {
      return false
    }
  }

  const clear = () => {
    localStorage.removeItem(storageKey)
    hasDraft.value = false
    lastSavedAt.value = null
  }

  // 表单数据变化时自动防抖保存
  watch(
    () => ({ ...formData }),
    debouncedSave,
    { deep: true },
  )

  // 挂载时检测是否已存在草稿, 用于页面提示恢复
  // (仅在组件 setup 内调用时注册; 在组件外/单测直接调用时跳过, 避免生命周期告警)
  if (getCurrentInstance()) {
    onMounted(() => {
      const raw = localStorage.getItem(storageKey)
      if (raw) {
        hasDraft.value = true
      }
    })
  }

  return { hasDraft, lastSavedAt, save, restore, clear, debouncedSave }
}
