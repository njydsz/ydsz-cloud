/**
 * @file 全局键盘快捷键 composable
 * @description 提供统一的键盘快捷键注册与管理能力：
 *   - 支持 Ctrl/Cmd + 字母组合快捷键
 *   - 支持单键快捷键（如 Esc、F5）
 *   - 自动在 input/textarea/contenteditable 中忽略单键快捷键
 *   - 支持快捷键描述（用于快捷键帮助面板展示）
 *   - 组件卸载时自动清理监听
 * @module composables/useKeyboardShortcuts
 *
 * 全局快捷键定义：
 *   Ctrl/Cmd + K  → 全局搜索
 *   Ctrl/Cmd + N  → 新建（当前页面）
 *   Ctrl/Cmd + S  → 保存（当前表单）
 *   Ctrl/Cmd + F  → 聚焦搜索框
 *   Esc           → 关闭对话框/抽屉
 *   Ctrl/Cmd + ,  → 设置
 *   Ctrl/Cmd + /  → 快捷键帮助
 *
 * 用法：
 *   // 全局注册（在 App.vue 或 layout 中）
 *   const { register, unregister } = useKeyboardShortcuts()
 *   register({ key: 'k', ctrl: true, handler: openSearch, description: '全局搜索' })
 *
 *   // 页面级注册（在业务页面 setup 中）
 *   const { register } = useKeyboardShortcuts()
 *   register({ key: 's', ctrl: true, handler: handleSave, description: '保存表单', scope: 'page' })
 */

import { onMounted, onUnmounted, ref, readonly } from 'vue'

/** 快捷键定义 */
export interface ShortcutDef {
  /** 按键（不区分大小写），如 'k', 's', 'Escape', 'F1' */
  key: string
  /** 是否需要 Ctrl (Windows) 或 Meta (Mac) 修饰键 */
  ctrl?: boolean
  /** 是否需要 Shift 修饰键 */
  shift?: boolean
  /** 是否需要 Alt 修饰键 */
  alt?: boolean
  /** 快捷键处理函数 */
  handler: (event: KeyboardEvent) => void
  /** 快捷键描述（用于帮助面板展示） */
  description?: string
  /**
   * 作用域：
   *   - 'global'  全局生效（默认）
   *   - 'page'    仅当前页面生效（组件卸载时自动注销）
   */
  scope?: 'global' | 'page'
  /**
   * 是否在输入框中触发（默认 false，输入时不触发单键快捷键）
   * 组合键（Ctrl/Cmd + X）始终触发
   */
  allowInInput?: boolean
}

/** 已注册的快捷键列表 */
const shortcuts = ref<ShortcutDef[]>([])

/** 快捷键帮助面板是否可见 */
const helpVisible = ref(false)

/** 判断当前焦点是否在输入元素中 */
function isInputFocused(): boolean {
  const el = document.activeElement
  if (!el) return false
  const tag = el.tagName.toLowerCase()
  return tag === 'input' || tag === 'textarea' || tag === 'select' || (el as HTMLElement).isContentEditable
}

/** 匹配快捷键 */
function matchShortcut(event: KeyboardEvent, def: ShortcutDef): boolean {
  const key = event.key.toLowerCase()
  const defKey = def.key.toLowerCase()

  // 按键匹配（支持 'Escape' → 'esc', 'Enter' → 'enter' 等）
  if (key !== defKey && key !== defKey.replace('escape', 'esc')) {
    // 特殊处理：Escape 的 event.key 是 'Escape'
    if (def.key.toLowerCase() === 'escape' && key === 'escape') {
      // match
    } else if (key !== defKey) {
      return false
    }
  }

  // 修饰键匹配（Mac 上 Cmd 对应 metaKey，Windows 上 Ctrl 对应 ctrlKey）
  const isMac = navigator.platform.toLowerCase().includes('mac')
  const ctrlPressed = isMac ? event.metaKey : event.ctrlKey
  if (def.ctrl && !ctrlPressed) return false
  if (!def.ctrl && ctrlPressed && !def.alt && !def.shift) {
    // 如果定义中不需要 ctrl 但用户按了 ctrl，不匹配（避免冲突）
    // 但允许 ctrl + 其他非字母键
  }

  if (def.shift && !event.shiftKey) return false
  if (def.alt && !event.altKey) return false

  return true
}

/** 全局键盘事件处理器 */
function handleKeydown(event: KeyboardEvent): void {
  // 快捷键帮助：Ctrl/Cmd + /
  if ((event.ctrlKey || event.metaKey) && event.key === '/') {
    event.preventDefault()
    helpVisible.value = !helpVisible.value
    return
  }

  // 遍历所有快捷键（page 作用域优先于 global）
  const sorted = [...shortcuts.value].sort((a, b) => {
    if (a.scope === 'page' && b.scope === 'global') return -1
    if (a.scope === 'global' && b.scope === 'page') return 1
    return 0
  })

  for (const def of sorted) {
    if (matchShortcut(event, def)) {
      // 在输入框中时，只触发组合键或 allowInInput 的快捷键
      if (isInputFocused() && !def.ctrl && !def.allowInInput) {
        continue
      }
      event.preventDefault()
      def.handler(event)
      return
    }
  }
}

/** 是否已初始化全局监听 */
let initialized = false

/** 初始化全局键盘监听 */
function initGlobalListener(): void {
  if (initialized) return
  window.addEventListener('keydown', handleKeydown, { capture: true })
  initialized = true
}

/** 销毁全局键盘监听（应用级清理时调用） */
export function destroyGlobalListener(): void {
  if (!initialized) return
  window.removeEventListener('keydown', handleKeydown, { capture: true })
  initialized = false
}

/**
 * 键盘快捷键 composable
 *
 * 在组件 setup 中调用，自动管理 page 作用域快捷键的生命周期。
 * global 作用域的快捷键在注册后持续生效，需手动 unregister。
 */
export function useKeyboardShortcuts() {
  /** 页面级快捷键列表（组件卸载时自动清理） */
  const pageShortcuts: ShortcutDef[] = []

  /**
   * 注册快捷键
   * @returns 注销函数
   */
  function register(def: ShortcutDef): () => void {
    const scope = def.scope || 'global'
    const fullDef = { ...def, scope }

    shortcuts.value.push(fullDef)
    if (scope === 'page') {
      pageShortcuts.push(fullDef)
    }

    // 返回注销函数
    return () => unregister(fullDef)
  }

  /** 注销快捷键 */
  function unregister(def: ShortcutDef): void {
    const idx = shortcuts.value.indexOf(def)
    if (idx > -1) {
      shortcuts.value.splice(idx, 1)
    }
    const pIdx = pageShortcuts.indexOf(def)
    if (pIdx > -1) {
      pageShortcuts.splice(pIdx, 1)
    }
  }

  /** 注销所有页面级快捷键 */
  function unregisterAllPage(): void {
    pageShortcuts.forEach((s) => {
      const idx = shortcuts.value.indexOf(s)
      if (idx > -1) shortcuts.value.splice(idx, 1)
    })
    pageShortcuts.length = 0
  }

  // 组件挂载时初始化全局监听
  onMounted(() => {
    initGlobalListener()
  })

  // 组件卸载时清理页面级快捷键
  onUnmounted(() => {
    unregisterAllPage()
  })

  return {
    /** 注册快捷键 */
    register,
    /** 注销快捷键 */
    unregister,
    /** 获取所有已注册快捷键（只读） */
    shortcuts: readonly(shortcuts),
    /** 快捷键帮助面板可见性 */
    helpVisible,
    /** 切换帮助面板 */
    toggleHelp: () => { helpVisible.value = !helpVisible.value },
  }
}
