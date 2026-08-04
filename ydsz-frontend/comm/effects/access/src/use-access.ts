/**
 * use-access 模块
 *
 * 提供三种粒度的权限判断：
 * - 按钮级：hasAccessByCodes / hasAccessByRoles
 * - 数据级（行级）：hasDataScope / getDataScope
 * - 数据级（字段级）：getFieldPermission / applyFieldMask
 *
 * @path comm\effects\access\src\use-access.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { computed } from 'vue';

import { preferences, updatePreferences } from '@ydsz/preferences';
import { useAccessStore, useUserStore } from '@ydsz/stores';

/** 字段访问模式 */
export type FieldAccessMode = 'hidden' | 'mask' | 'read';

/** 脱敏字符替换策略：保留首末字符，中间用 * 替换 */
function maskValue(value: unknown): string {
  if (value === null || value === undefined) return '';
  const str = String(value);
  if (str.length <= 2) return '*'.repeat(str.length);
  if (str.length <= 6) return `${str[0]}${'*'.repeat(str.length - 2)}${str[str.length - 1]}`;
  return `${str.slice(0, 2)}${'*'.repeat(4)}${str.slice(-2)}`;
}

function useAccess() {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const accessMode = computed(() => {
    return preferences.app.accessMode;
  });

  /**
   * 基于角色判断是否有权限
   * @description: Determine whether there is permission，The role is judged by the user's role
   * @param roles
   */
  function hasAccessByRoles(roles: string[]) {
    const userRoleSet = new Set(userStore.userRoles);
    const intersection = roles.filter((item) => userRoleSet.has(item));
    return intersection.length > 0;
  }

  /**
   * 基于权限码判断是否有权限
   * @description: Determine whether there is permission，The permission code is judged by the user's permission code
   * @param codes
   */
  function hasAccessByCodes(codes: string[]) {
    const userCodesSet = new Set(accessStore.accessCodes);

    const intersection = codes.filter((item) => userCodesSet.has(item));
    return intersection.length > 0;
  }

  /**
   * 获取指定资源的数据范围约束（行级数据权限）。
   *
   * 后端在登录或权限校验接口返回 dataScopes，键为资源码（如 'project:budget'），
   * 值为该资源的数据范围约束。业务侧据此构造查询参数（如 deptId in [...]）。
   *
   * 返回 undefined 表示该资源未受数据权限约束，可访问全部数据。
   *
   * @param resourceCode - 资源码，如 'project:budget'
   * @returns 数据范围约束，或 undefined
   */
  function getDataScope<T = unknown>(resourceCode: string): T | undefined {
    const scope = accessStore.dataScopes[resourceCode];
    return scope as T | undefined;
  }

  /**
   * 判断是否拥有指定资源的数据访问范围（行级数据权限）。
   *
   * - 若 dataScopes 中不存在该 resourceCode，视为未受限，返回 true。
   * - 若存在且值为空数组/空对象/null，视为完全受限，返回 false。
   * - 否则返回 true（业务侧应进一步使用 getDataScope 获取具体范围约束）。
   *
   * @param resourceCode - 资源码
   */
  function hasDataScope(resourceCode: string): boolean {
    const scope = accessStore.dataScopes[resourceCode];
    if (scope === undefined || scope === null) return true;
    if (Array.isArray(scope)) return scope.length > 0;
    if (typeof scope === 'object') return Object.keys(scope).length > 0;
    return Boolean(scope);
  }

  /**
   * 获取字段访问模式（字段级数据权限）。
   *
   * @param fieldKey - 字段标识，如 'project.budget.amount'
   * @returns 访问模式：'read'（默认）/ 'mask'（脱敏）/ 'hidden'（隐藏）
   */
  function getFieldPermission(fieldKey: string): FieldAccessMode {
    return accessStore.fieldPermissions[fieldKey] ?? 'read';
  }

  /**
   * 根据字段权限对值进行脱敏或隐藏处理。
   *
   * - 'hidden'：返回空字符串（表格/表单中应配合 v-if 隐藏列/字段）
   * - 'mask'：返回脱敏后的字符串（保留首末字符，中间用 * 替换）
   * - 'read'：原样返回
   *
   * @param fieldKey - 字段标识
   * @param value - 原始值
   */
  function applyFieldMask(fieldKey: string, value: unknown): string {
    const mode = getFieldPermission(fieldKey);
    if (mode === 'hidden') return '';
    if (mode === 'mask') return maskValue(value);
    return value === null || value === undefined ? '' : String(value);
  }

  async function toggleAccessMode() {
    updatePreferences({
      app: {
        accessMode:
          preferences.app.accessMode === 'frontend' ? 'backend' : 'frontend',
      },
    });
  }

  return {
    accessMode,
    applyFieldMask,
    getFieldPermission,
    getDataScope,
    hasAccessByCodes,
    hasAccessByRoles,
    hasDataScope,
    toggleAccessMode,
  };
}

export { useAccess };
