/**
 * 全局权限指令
 * 用于组件级别的细粒度权限控制
 * @example v-access:role="[ROLE_NAME]" 或 v-access:role="ROLE_NAME"
 * @example v-access:code="[ROLE_CODE]" 或 v-access:code="ROLE_CODE"
 */
import type { App, Directive, DirectiveBinding } from 'vue';

import { useAccess } from './use-access';

/**
 * 标记被权限指令隐藏的元素，便于 updated 钩子恢复。
 *
 * 不使用 el.remove() 物理移除，而是 display:none + 标记属性，
 * 这样权限变更（如角色切换、权限码刷新）时 updated 钩子可以恢复显示。
 */
const HIDDEN_ATTR = 'data-access-hidden';
/** 记录元素原始的 display 值，恢复时还原，避免破坏布局 */
const DISPLAY_RESTORE_ATTR = 'data-access-original-display';

/** 缓存 useAccess 返回值，避免在指令钩子中重复调用 */
let cachedAccess: ReturnType<typeof useAccess> | null = null;

function getAccess() {
  if (!cachedAccess) {
    cachedAccess = useAccess();
  }
  return cachedAccess;
}

function checkAccess(
  _el: Element,
  binding: DirectiveBinding<string | string[]>,
): boolean {
  const { accessMode, hasAccessByCodes, hasAccessByRoles } = getAccess();
  const value = binding.value;

  if (!value) return true;

  const authMethod =
    accessMode.value === 'frontend' && binding.arg === 'role'
      ? hasAccessByRoles
      : hasAccessByCodes;

  const values = Array.isArray(value) ? value : [value];
  return authMethod(values);
}

/** 隐藏元素：记录原 display → 设置 display:none → 标记属性 */
function hideElement(el: Element): void {
  const htmlEl = el as HTMLElement;
  if (!htmlEl.hasAttribute(DISPLAY_RESTORE_ATTR)) {
    htmlEl.setAttribute(DISPLAY_RESTORE_ATTR, htmlEl.style.display || '');
  }
  htmlEl.style.display = 'none';
  htmlEl.setAttribute(HIDDEN_ATTR, 'true');
}

/** 恢复元素：还原原 display → 移除标记属性 */
function showElement(el: Element): void {
  const htmlEl = el as HTMLElement;
  const originalDisplay = htmlEl.getAttribute(DISPLAY_RESTORE_ATTR);
  htmlEl.style.display = originalDisplay || '';
  htmlEl.removeAttribute(DISPLAY_RESTORE_ATTR);
  htmlEl.removeAttribute(HIDDEN_ATTR);
}

const mounted = (el: Element, binding: DirectiveBinding<string | string[]>) => {
  if (!checkAccess(el, binding)) {
    hideElement(el);
  }
};

/** 权限变更时重新评估（如角色切换、权限码刷新），支持恢复显示 */
const updated = (el: Element, binding: DirectiveBinding<string | string[]>) => {
  // 元素已被外部逻辑移除时跳过
  if (!document.contains(el)) return;
  const hasAccess = checkAccess(el, binding);
  const wasHidden = el.hasAttribute(HIDDEN_ATTR);

  if (!hasAccess && !wasHidden) {
    // 之前可见，现在无权限 → 隐藏
    hideElement(el);
  } else if (hasAccess && wasHidden) {
    // 之前隐藏，现在有权限 → 恢复显示
    showElement(el);
  }
};

const authDirective: Directive = {
  mounted,
  updated,
};

/**
 * 向 Vue 应用注册全局权限指令 `v-access`。
 *
 * @remarks
 * 匹配规则：仅当 `accessMode === 'frontend'` 且指令参数为 `role` 时按角色（`hasAccessByRoles`）匹配，
 * 其余情况（含 `v-access:code`、后端权限模式下的 `v-access:role`）一律按权限码（`hasAccessByCodes`）匹配。
 * 指令值可以是单个字符串或字符串数组，数组语义为「命中任意一项即放行」。
 *
 * 失败表现：鉴权不通过时设置 `display: none` + `data-access-hidden` 属性，
 * **不物理移除元素**，权限变更后 updated 钩子可自动恢复显示。
 *
 * 生命周期：实现了 `mounted` + `updated`，支持元素属性更新与权限变更时重新评估。
 *
 * @param app - 需要注册指令的 Vue 应用实例，通常在应用启动阶段调用一次
 *
 * @example
 * ```ts
 * registerAccessDirective(app);
 * ```
 * ```html
 * <button v-access:code="'AC_100100'">新增</button>
 * <button v-access:role="['super', 'admin']">删除</button>
 * ```
 */
export function registerAccessDirective(app: App) {
  app.directive('access', authDirective);
}
