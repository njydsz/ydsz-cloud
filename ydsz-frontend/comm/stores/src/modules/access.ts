/**
 * access Pinia 状态管理 — 访问控制（menus/routes/codes）
 *
 * 注意：认证相关状态（token/refreshToken/lockScreen）已迁移到 useAuthStore，
 * 本 store 保留兼容的 getter/setter 代理，推荐新代码直接使用 useAuthStore。
 *
 * @path comm\stores\src\modules\access.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { RouteRecordRaw } from 'vue-router';

import type { MenuRecordRaw } from '@ydsz-core/typings';

import { acceptHMRUpdate, defineStore } from 'pinia';

import { useTokenStore } from './auth';

type AccessToken = null | string;

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
}

/**
 * @zh_CN 访问权限相关（menus/routes/codes）
 *
 * 认证 Token 相关操作请使用 useAuthStore。
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
    /** @deprecated 请使用 useTokenStore().lockScreen() */
    async lockScreen(password: string) {
      return useTokenStore().lockScreen(password);
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
    /** @deprecated 请使用 useTokenStore().setAccessToken() */
    setAccessToken(token: AccessToken) {
      useTokenStore().setAccessToken(token);
    },
    setIsAccessChecked(isAccessChecked: boolean) {
      this.isAccessChecked = isAccessChecked;
    },
    /** @deprecated 请使用 useTokenStore().setLoginExpired() */
    setLoginExpired(loginExpired: boolean) {
      useTokenStore().setLoginExpired(loginExpired);
    },
    /** @deprecated 请使用 useTokenStore().setRefreshToken() */
    setRefreshToken(token: AccessToken) {
      useTokenStore().setRefreshToken(token);
    },
    /** @deprecated 请使用 useTokenStore().unlockScreen() */
    unlockScreen() {
      useTokenStore().unlockScreen();
    },
    /** @deprecated 请使用 useTokenStore().verifyLockScreenPassword() */
    async verifyLockScreenPassword(password: string): Promise<boolean> {
      return useTokenStore().verifyLockScreenPassword(password);
    },
  },
  getters: {
    /** 代理到 useTokenStore.accessToken */
    accessToken(): AccessToken {
      return useTokenStore().accessToken;
    },
    /** 代理到 useTokenStore.isLockScreen */
    isLockScreen(): boolean {
      return useTokenStore().isLockScreen;
    },
    /** 代理到 useTokenStore.loginExpired */
    loginExpired(): boolean {
      return useTokenStore().loginExpired;
    },
    /** 代理到 useTokenStore.lockScreenPassword */
    lockScreenPassword(): string | undefined {
      return useTokenStore().lockScreenPassword;
    },
    /** 代理到 useTokenStore.refreshToken */
    refreshToken(): AccessToken {
      return useTokenStore().refreshToken;
    },
  },
  persist: {
    // ⚠️ 迁移说明：accessToken/refreshToken/isLockScreen/lockScreenPassword
    // 已迁移到 useAuthStore 持久化，此处仅保留 accessCodes
    pick: ['accessCodes'],
  },
  state: (): AccessState => ({
    accessCodes: [],
    accessMenus: [],
    accessRoutes: [],
    isAccessChecked: false,
  }),
});

// 解决热更新问题
const hot = import.meta.hot;
if (hot) {
  hot.accept(acceptHMRUpdate(useAccessStore, hot));
}
