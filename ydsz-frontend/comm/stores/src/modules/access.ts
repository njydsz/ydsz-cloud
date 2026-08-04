/**
 * access Pinia 状态管理 — 访问控制（menus/routes/codes）
 *
 * 认证相关状态（token/refreshToken/lockScreen）由 useTokenStore 管理，
 * 本 store 仅负责权限码、菜单、路由的存取。
 *
 * @path comm\stores\src\modules\access.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { RouteRecordRaw } from 'vue-router';

import type { MenuRecordRaw } from '@ydsz-core/typings';

import { acceptHMRUpdate, defineStore } from 'pinia';

interface AccessState {
  /**
   * 权限码
   */
  accessCodes: string[];
  /**
   * 可访问的菜单列表
   */
  accessMenus: MenuRecordRaw[];
  /**
   * 可访问的路由列表
   */
  accessRoutes: RouteRecordRaw[];
  /**
   * 是否已经检查过权限
   */
  isAccessChecked: boolean;
  /**
   * 数据权限范围（行级）。
   *
   * 后端返回的资源访问范围清单，键为资源码（如 'project:budget'），
   * 值为该资源的数据范围约束（如部门 ID 列表、项目 ID 列表等）。
   * undefined/null 表示未受限，可访问全部数据。
   *
   * @since 3.4.0
   */
  dataScopes: Record<string, unknown>;
  /**
   * 字段级权限清单。
   *
   * 后端返回的字段访问控制清单，键为字段标识（如 'project.budget.amount'），
   * 值为访问模式：'read'（只读）/ 'mask'（脱敏展示）/ 'hidden'（隐藏）。
   *
   * @since 3.4.0
   */
  fieldPermissions: Record<string, 'mask' | 'hidden' | 'read'>;
}

/**
 * @zh_CN 访问权限相关（menus/routes/codes）
 *
 * 认证 Token 相关操作请使用 useTokenStore。
 */
export const useAccessStore = defineStore('core-access', {
  actions: {
    getMenuByPath(path: string) {
      function findMenu(
        menus: MenuRecordRaw[],
        path: string,
      ): MenuRecordRaw | undefined {
        for (const menu of menus) {
          if (menu.path === path) {
            return menu;
          }
          if (menu.children) {
            const matched = findMenu(menu.children, path);
            if (matched) {
              return matched;
            }
          }
        }
      }
      return findMenu(this.accessMenus, path);
    },
    setAccessCodes(codes: string[]) {
      this.accessCodes = codes;
    },
    setAccessMenus(menus: MenuRecordRaw[]) {
      this.accessMenus = menus;
    },
    setAccessRoutes(routes: RouteRecordRaw[]) {
      this.accessRoutes = routes;
    },
    setDataScopes(scopes: Record<string, unknown>) {
      this.dataScopes = scopes;
    },
    setFieldPermissions(perms: Record<string, 'mask' | 'hidden' | 'read'>) {
      this.fieldPermissions = perms;
    },
    setIsAccessChecked(isAccessChecked: boolean) {
      this.isAccessChecked = isAccessChecked;
    },
  },
  persist: {
    pick: ['accessCodes'],
  },
  state: (): AccessState => ({
    accessCodes: [],
    accessMenus: [],
    accessRoutes: [],
    isAccessChecked: false,
    dataScopes: {},
    fieldPermissions: {},
  }),
});

// 解决热更新问题
const hot = import.meta.hot;
if (hot) {
  hot.accept(acceptHMRUpdate(useAccessStore, hot));
}
