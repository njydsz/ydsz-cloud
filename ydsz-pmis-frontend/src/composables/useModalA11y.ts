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
 *     3. Escape 键关闭：兜底监听 Escape 键关闭对话框（ElDialog 内部已有，此处做防御性补全）
 *     4. 屏幕阅读器公告：打开/关闭时通过 aria-live 区域向辅助技术发送状态变更
 *     5. 背景惰性化：打开时给背景 DOM 设置 aria-hidden，避免屏幕阅读器误读
 *
 * 使用方式：
 *   ```ts
 *   const dialogVisible = ref(false)
 *   const triggerRef = ref<HTMLElement | null>(null)
 *   useModalA11y(dialogVisible, { triggerEl: triggerRef })
 *   ```
 *   模板中为触发按钮添加 ref="triggerRef" 即可，关闭时焦点自动回到该按钮。
 */
import { watch, nextTick, onUnmounted, type Ref } from 'vue'

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
  /**
   * 对话框标题（用于屏幕阅读器公告）。
   * 传入后，打开对话框时会播报 "对话框已打开：{title}"。
   */
  title?: string
  /**
   * 是否启用 Escape 键关闭（默认 true）。
   * ElDialog 自身已支持 Escape 关闭，此选项为防御性兜底。
   * 如需禁用（例如表单未保存时），设置为 false。
   */
  escapeClose?: boolean
  /**
   * 自定义 Escape 关闭回调。
   * 传入后会替代默认的 visible.value = false 逻辑。
   */
  onEscape?: () => void
}

/** useModalA11y 返回值 */
export interface UseModalA11yReturn {
  /** 手动恢复焦点到触发器（或打开前焦点元素） */
  restoreFocus: () => void
}

/** aria-live 公告区域 ID */
const LIVE_REGION_ID = '__modal_a11y_live_region__'

/**
 * 获取或创建全局 aria-live 公告区域。
 * 该区域视觉不可见，但屏幕阅读器会朗读其内容变更。
 */
function getLiveRegion(): HTMLElement {
  let region = document.getElementById(LIVE_REGION_ID)
  if (!region) {
    region = document.createElement('div')
    region.id = LIVE_REGION_ID
    region.setAttribute('aria-live', 'polite')
    region.setAttribute('aria-atomic', 'true')
    region.setAttribute('role', 'status')
    // 视觉隐藏但保留辅助技术可读
    region.style.cssText =
      'position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0;'
    document.body.appendChild(region)
  }
  return region
}

/**
 * 向屏幕阅读器发送公告。
 * @param message 公告内容
 */
function announce(message: string): void {
  const region = getLiveRegion()
  // 先清空再设置，确保内容变更触发朗读
  region.textContent = ''
  // 使用 setTimeout 确保辅助技术能检测到变更
  window.setTimeout(() => {
    region.textContent = message
  }, 50)
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
  const {
    triggerEl,
    selector = '.el-dialog[aria-modal="true"]',
    title,
    escapeClose = true,
    onEscape,
  } = options

  // 打开对话框前的焦点元素，关闭后用于恢复
  let previouslyFocused: HTMLElement | null = null
  // 被标记为 aria-hidden 的背景元素列表，关闭后需恢复
  let hiddenElements: HTMLElement[] = []
  // Escape 键监听器引用
  let keydownHandler: ((e: KeyboardEvent) => void) | null = null

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

  /**
   * 将对话框之外的顶层元素标记为 aria-hidden，
   * 防止屏幕阅读器在对话框打开时朗读背景内容。
   * 关闭时恢复原始状态。
   */
  function markBackgroundAriaHidden(): void {
    hiddenElements = []
    const dialogOverlay = document.querySelector('.el-overlay')
    const bodyChildren = Array.from(document.body.children) as HTMLElement[]
    for (const child of bodyChildren) {
      // 跳过对话框遮罩层自身、script 标签、aria-live 区域
      if (
        child === dialogOverlay ||
        child.tagName === 'SCRIPT' ||
        child.id === LIVE_REGION_ID
      ) {
        continue
      }
      // 跳过已经是 aria-hidden 的元素
      if (child.getAttribute('aria-hidden') === 'true') {
        continue
      }
      child.setAttribute('aria-hidden', 'true')
      hiddenElements.push(child)
    }
  }

  /** 恢复背景元素的 aria-hidden 状态 */
  function restoreBackgroundAriaHidden(): void {
    for (const el of hiddenElements) {
      el.removeAttribute('aria-hidden')
    }
    hiddenElements = []
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

  /** 注册 Escape 键监听 */
  function registerEscapeHandler(): void {
    if (!escapeClose) return
    keydownHandler = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && visible.value) {
        e.stopPropagation()
        if (onEscape) {
          onEscape()
        } else {
          visible.value = false
        }
      }
    }
    document.addEventListener('keydown', keydownHandler, true)
  }

  /** 移除 Escape 键监听 */
  function unregisterEscapeHandler(): void {
    if (keydownHandler) {
      document.removeEventListener('keydown', keydownHandler, true)
      keydownHandler = null
    }
  }

  watch(visible, async (val) => {
    if (val) {
      // 打开时记录当前焦点元素，等待 DOM 渲染后确保 aria-modal
      previouslyFocused = document.activeElement as HTMLElement
      await nextTick()
      ensureAriaModal()
      markBackgroundAriaHidden()
      registerEscapeHandler()
      // 屏幕阅读器公告
      const announceText = title
        ? `对话框已打开：${title}`
        : '对话框已打开'
      announce(announceText)
    } else {
      // 关闭时恢复焦点和背景状态
      unregisterEscapeHandler()
      restoreBackgroundAriaHidden()
      restoreFocus()
      // 屏幕阅读器公告
      const announceText = title
        ? `对话框已关闭：${title}`
        : '对话框已关闭'
      announce(announceText)
    }
  })

  // 组件卸载时清理
  onUnmounted(() => {
    unregisterEscapeHandler()
    restoreBackgroundAriaHidden()
  })

  return {
    restoreFocus,
  }
}
