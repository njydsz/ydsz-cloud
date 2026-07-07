/**
 * @file 模态框无障碍访问增强 composable
 * @description 补充 Element Plus ElDialog 未覆盖的 a11y 能力，满足 WAI-ARIA 无障碍访问标准。
 * @module composables/useModalA11y
 *
 * 背景：
 *   Element Plus 2.x 的 ElDialog 已内置 focus trap（通过 el-focus-trap 组件实现），
 *   打开时 Tab 键焦点不会跳出对话框，且默认已设置 aria-modal="true" 与 role="dialog"。
 *   本 composable 在此基础上补充：
 *     1. 焦点恢复：关闭对话框后将焦点返回到触发器元素（或打开前的 activeElement）
 *     2. aria-modal 属性确保：兜底设置，避免某些场景下属性缺失
 *
 * 使用方式：
 *   ```ts
 *   const dialogVisible = ref(false)
 *   const triggerRef = ref<HTMLElement | null>(null)
 *   useModalA11y(dialogVisible, { triggerEl: triggerRef })
 *   ```
 *   模板中为触发按钮添加 ref="triggerRef" 即可，关闭时焦点自动回到该按钮。
 */
import { watch, nextTick, type Ref } from 'vue'

/** useModalA11y 配置项 */
export interface UseModalA11yOptions {
  /**
   * 触发器元素 ref，关闭后恢复焦点到此元素。
   * 不传时恢复到打开对话框前的 document.activeElement。
   */
  triggerEl?: Ref<HTMLElement | null>
  /**
   * 对话框根元素的选择器，用于兜底设置 aria-modal 属性。
   * 默认取 Element Plus 渲染出的 .el-dialog[aria-modal="true"]。
   */
  selector?: string
}

/** useModalA11y 返回值 */
export interface UseModalA11yReturn {
  /** 手动恢复焦点到触发器（或打开前焦点元素） */
  restoreFocus: () => void
}

/**
 * 模态框无障碍访问增强
 *
 * 必须在组件 setup 中调用（内部使用 watch，依赖组件作用域自动清理）。
 *
 * @param visible 对话框可见性 ref
 * @param options 配置项
 * @returns `{ restoreFocus }`
 */
export function useModalA11y(
  visible: Ref<boolean>,
  options: UseModalA11yOptions = {},
): UseModalA11yReturn {
  const { triggerEl, selector = '.el-dialog[aria-modal="true"]' } = options

  // 打开对话框前的焦点元素，关闭后用于恢复
  let previouslyFocused: HTMLElement | null = null

  /**
   * 兜底确保对话框根节点存在 aria-modal="true"。
   * Element Plus 默认已设置，此处仅作防御性补全。
   */
  function ensureAriaModal(): void {
    const modal = document.querySelector(selector)
    if (modal && !modal.getAttribute('aria-modal')) {
      modal.setAttribute('aria-modal', 'true')
    }
  }

  /** 关闭对话框后将焦点恢复到触发器或打开前焦点元素 */
  function restoreFocus(): void {
    // 优先恢复到显式传入的触发器元素，否则恢复到打开前的 activeElement
    const target = triggerEl?.value || previouslyFocused
    if (target && typeof target.focus === 'function') {
      target.focus()
    }
    previouslyFocused = null
  }

  watch(visible, async (val) => {
    if (val) {
      // 打开时记录当前焦点元素，等待 DOM 渲染后确保 aria-modal
      previouslyFocused = document.activeElement as HTMLElement
      await nextTick()
      ensureAriaModal()
    } else {
      // 关闭时恢复焦点
      restoreFocus()
    }
  })

  return {
    restoreFocus,
  }
}
