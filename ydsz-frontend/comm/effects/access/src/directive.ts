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

/**
 * 向 Vue 应用注册全局权限指令 `v-access`。
 *
 * @remarks
 * 匹配规则：仅当 `accessMode === 'frontend'` 且指令参数为 `role` 时按角色（`hasAccessByRoles`）匹配，
 * 其余情况（含 `v-access:code`、后端权限模式下的 `v-access:role`）一律按权限码（`hasAccessByCodes`）匹配。
 * 指令值可以是单个字符串或字符串数组，数组语义为「命中任意一项即放行」。
 *
 * 失败表现：鉴权不通过时直接调用 `el.remove()` 将宿主元素从 DOM 中**物理移除**，
 * 而非隐藏，因此该元素的子组件会被销毁且无法恢复；指令值为空（`undefined`/`''`/`0`）时视为不做校验，元素保留。
 *
 * 生命周期：只实现了 `mounted` 钩子，权限判定**仅在挂载时执行一次**。
 * 若用户权限在运行时发生变更（如重新登录、切换角色），需要重新渲染组件（例如刷新路由 key）才能生效。
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
