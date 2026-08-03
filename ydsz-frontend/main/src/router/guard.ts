/**
 * guard 路由模块
 *
 * @path main\src\router\guard.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Router } from 'vue-router';

import { LOGIN_PATH } from '@ydsz/constants';
import { preferences } from '@ydsz/preferences';
import { useAccessStore, useUserStore } from '@ydsz/stores';
import { setupCommonGuard } from '@ydsz/shared-auth/guards';

import { accessRoutes, coreRouteNames } from '#/router/routes';
import { useAuthStore } from '#/store';

import { generateAccess } from './access';

/**
 * 权限访问守卫：校验登录态并生成动态路由。
 *
 * 主应用特有逻辑：fetchUserInfo + generateAccess + 角色/菜单生成。
 * 子应用使用 shared-auth 的简化版 createSubAppRouterGuard。
 *
 * @param router - Vue Router 实例
 */
function setupAccessGuard(router: Router) {
  router.beforeEach(async (to, from) => {
    const accessStore = useAccessStore();
    const userStore = useUserStore();
    const authStore = useAuthStore();

    // 基本路由，这些路由不需要进入权限拦截
    if (coreRouteNames.includes(to.name as string)) {
      if (to.path === LOGIN_PATH && accessStore.accessToken) {
        return decodeURIComponent(
          (to.query?.redirect as string) ||
            userStore.userInfo?.homePath ||
            preferences.app.defaultHomePath,
        );
      }
      return true;
    }

    // accessToken 检查
    if (!accessStore.accessToken) {
      // 明确声明忽略权限访问权限，则可以访问
      if (to.meta.ignoreAccess) {
        return true;
      }

      // 没有访问权限，跳转登录页面
      if (to.fullPath !== LOGIN_PATH) {
        return {
          path: LOGIN_PATH,
          // 如不需要，直接删除 query
          query:
            to.fullPath === preferences.app.defaultHomePath
              ? {}
              : { redirect: encodeURIComponent(to.fullPath) },
          // 携带当前跳转的页面，登录后重新跳转该页面
          replace: true,
        };
      }
      return to;
    }

    // 是否已经生成过动态路由
    if (accessStore.isAccessChecked) {
      return true;
    }

    // 生成路由表
    // 当前登录用户拥有的角色标识列表
    const userInfo = userStore.userInfo || (await authStore.fetchUserInfo());
    const userRoles = userInfo.roles ?? [];

    // 生成菜单和路由
    const { accessibleMenus, accessibleRoutes } = await generateAccess({
      roles: userRoles,
      router,
      // 则会在菜单中显示，但是访问会被重定向到403
      routes: accessRoutes,
    });

    // 保存菜单信息和路由信息
    accessStore.setAccessMenus(accessibleMenus);
    accessStore.setAccessRoutes(accessibleRoutes);
    accessStore.setIsAccessChecked(true);

    // 添加动态路由到 Root 下
    accessibleRoutes.forEach((route) => {
      router.addRoute('Root', route);
    });

    const redirectPath = (from.query.redirect ??
      (to.path === preferences.app.defaultHomePath
        ? userInfo.homePath || preferences.app.defaultHomePath
        : to.fullPath)) as string;

    return {
      ...router.resolve(decodeURIComponent(redirectPath)),
      replace: true,
    };
  });
}

/**
 * 组装并注册全部路由守卫（通用 + 权限）。
 *
 * 主应用使用 shared-auth 的 setupCommonGuard（消除 25 行重复），
 * 再追加主应用专有的 setupAccessGuard。
 *
 * @param router - Vue Router 实例
 */
function createRouterGuard(router: Router) {
  /** 通用（来自 shared-auth 共享） */
  setupCommonGuard(router);
  /** 权限访问（主应用特有） */
  setupAccessGuard(router);
}

export { createRouterGuard };
