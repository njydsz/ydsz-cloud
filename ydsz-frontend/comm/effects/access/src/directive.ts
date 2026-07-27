/**
 * 全局权限指令
 * 用于组件级别的细粒度权限控制
 * @example v-access:role="[ROLE_NAME]" 或 v-access:role="ROLE_NAME"
 * @example v-access:code="[ROLE_CODE]" 或 v-access:code="ROLE_CODE"
 */
import type { App, Directive, DirectiveBinding } from 'vue';

import { useAccess } from './use-access';

/** 缓存 useAccess 返回值，避免在指令钩子中重复调用 */
let cachedAccess: ReturnType<typeof useAccess> | null = null;

function getAccess() {
  if (!cachedAccess) {
    cachedAccess = useAccess();
  }
  return cachedAccess;
}

function isAccessible(
  el: Element,
  binding: DirectiveBinding<string | string[]>,
) {
  const { accessMode, hasAccessByCodes, hasAccessByRoles } = getAccess();

  const value = binding.value;

  if (!value) return;
  const authMethod =
    accessMode.value === 'frontend' && binding.arg === 'role'
      ? hasAccessByRoles
      : hasAccessByCodes;

  const values = Array.isArray(value) ? value : [value];

  if (!authMethod(values)) {
    el?.remove();
  }
}

const mounted = (el: Element, binding: DirectiveBinding<string | string[]>) => {
  isAccessible(el, binding);
};

const authDirective: Directive = {
  mounted,
};

export function registerAccessDirective(app: App) {
  app.directive('access', authDirective);
}
