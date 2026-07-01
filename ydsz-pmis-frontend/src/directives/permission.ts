import type { App, Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/store/modules/user'

/**
 * v-permission 权限指令
 *
 * 用法:
 *   <el-button v-permission="'system:user:create'">新增</el-button>
 *   <el-button v-permission="['system:user:create', 'system:user:update']">新增</el-button>
 *   <el-button v-permission.all="['system:user:create', 'system:user:update']">管理员</el-button>
 *   <el-button v-permission.disabled="'system:user:delete'">删除（无权限时禁用而非隐藏）</el-button>
 *
 * 行为模式:
 *   - 默认: 无权限时 display: none 隐藏元素（保留原始 display，可恢复）
 *   - .disabled 修饰符: 无权限时元素 disabled + is-disabled class + title 提示
 *
 * 响应式:
 *   - 同时挂载 mounted 和 updated 钩子，权限动态调整时自动重新评估
 *   - 用户切换账号、权限刷新时无需手动刷新页面
 *
 * 兼容性:
 *   - 对 el-button 同时设置 disabled 属性（Element Plus 标准）
 *   - 对原生 div/span 设置 aria-disabled + pointer-events: none
 */

type PermissionValue = string | string[]

const DATA_ORIGINAL_DISPLAY = '__permOriginalDisplay'
const DATA_ORIGINAL_TITLE = '__permOriginalTitle'
const DATA_ORIGINAL_DISABLED = '__permOriginalDisabled'
const PERM_DISABLED_CLASS = 'perm-disabled'

function check(perm: PermissionValue, all = false): boolean {
  const userStore = useUserStore()
  const permissions = userStore.permissions

  // 超级权限
  if (permissions.includes('*:*:*')) return true

  if (typeof perm === 'string') {
    return permissions.includes(perm)
  }

  if (Array.isArray(perm)) {
    return all
      ? perm.every((p) => permissions.includes(p))
      : perm.some((p) => permissions.includes(p))
  }

  return false
}

/**
 * 默认模式：通过 display 控制显隐
 * - 隐藏前保存原始 display 值，恢复时还原
 * - 不再使用 removeChild 破坏性操作（原实现已废弃）
 */
function applyHiddenMode(el: HTMLElement, allowed: boolean): void {
  const dataset = el.dataset
  if (allowed) {
    // 恢复原始 display
    if (dataset[DATA_ORIGINAL_DISPLAY] !== undefined) {
      el.style.display = dataset[DATA_ORIGINAL_DISPLAY]
      delete dataset[DATA_ORIGINAL_DISPLAY]
    }
  } else {
    // 保存原始 display 并隐藏
    if (dataset[DATA_ORIGINAL_DISPLAY] === undefined) {
      dataset[DATA_ORIGINAL_DISPLAY] = el.style.display || ''
    }
    el.style.display = 'none'
  }
}

/**
 * disabled 模式：将元素设为禁用态 + 视觉提示
 * - el-button 等支持 disabled 属性的元素：直接设置 disabled
 * - 通用元素：通过 class + pointer-events 实现禁用视觉
 * - 添加 title 提示无权限原因
 */
function applyDisabledMode(el: HTMLElement, allowed: boolean): void {
  const dataset = el.dataset
  if (allowed) {
    // 恢复原始状态
    if (dataset[DATA_ORIGINAL_DISABLED] !== undefined) {
      if (dataset[DATA_ORIGINAL_DISABLED] === 'true') {
        el.setAttribute('disabled', 'disabled')
      } else {
        el.removeAttribute('disabled')
      }
      delete dataset[DATA_ORIGINAL_DISABLED]
    }
    if (dataset[DATA_ORIGINAL_TITLE] !== undefined) {
      if (dataset[DATA_ORIGINAL_TITLE]) {
        el.setAttribute('title', dataset[DATA_ORIGINAL_TITLE])
      } else {
        el.removeAttribute('title')
      }
      delete dataset[DATA_ORIGINAL_TITLE]
    }
    el.classList.remove(PERM_DISABLED_CLASS)
    el.style.cursor = ''
    el.removeAttribute('aria-disabled')
    el.style.pointerEvents = ''
    el.style.opacity = ''
  } else {
    // 保存原始状态
    if (dataset[DATA_ORIGINAL_DISABLED] === undefined) {
      dataset[DATA_ORIGINAL_DISABLED] = el.hasAttribute('disabled') ? 'true' : 'false'
    }
    if (dataset[DATA_ORIGINAL_TITLE] === undefined) {
      dataset[DATA_ORIGINAL_TITLE] = el.getAttribute('title') || ''
    }
    // 应用禁用态
    el.setAttribute('disabled', 'disabled')
    el.setAttribute('aria-disabled', 'true')
    el.classList.add(PERM_DISABLED_CLASS)
    el.style.cursor = 'not-allowed'
    el.style.pointerEvents = 'none'
    el.style.opacity = '0.5'
    el.setAttribute('title', '无权限执行此操作')
  }
}

function apply(el: HTMLElement, binding: DirectiveBinding<PermissionValue>): void {
  const { value, modifiers } = binding
  if (!value) return

  const allowed = check(value, modifiers?.all as boolean | undefined)

  if (modifiers?.disabled) {
    applyDisabledMode(el, allowed)
  } else {
    applyHiddenMode(el, allowed)
  }
}

const permissionDirective: Directive<HTMLElement, PermissionValue> = {
  mounted(el: HTMLElement, binding: DirectiveBinding<PermissionValue>) {
    apply(el, binding)
  },
  updated(el: HTMLElement, binding: DirectiveBinding<PermissionValue>) {
    apply(el, binding)
  },
}

export function setupPermissionDirective(app: App): void {
  app.directive('permission', permissionDirective)
}

export default permissionDirective
