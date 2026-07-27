import type { Router } from 'vue-router';

import { LOGIN_PATH } from '@ydsz/constants';
import { preferences } from '@ydsz/preferences';
import { useAccessStore } from '@ydsz/stores';
import { startProgress, stopProgress } from '@ydsz/utils';

import { accessRoutes } from '#/router/routes';

/**
 * 不需要登录即可访问的白名单路径
 */
const WHITE_LIST = ['/auth', LOGIN_PATH];

/**
 * 通用守卫：进度条控制和页面加载状态
 */
function setupCommonGuard(router: Router) {
  const loadedPaths = new Set<string>();

  router.beforeEach((to) => {
    to.meta.loaded = loadedPaths.has(to.path);

    if (!to.meta.loaded && preferences.transition.progress) {
      startProgress();
    }
    return true;
  });

  router.afterEach((to) => {
    loadedPaths.add(to.path);

    if (preferences.transition.progress) {
      stopProgress();
    }
  });
}

/**
 * 认证守卫：未登录用户访问受保护路由时跳转登录页
 */
function setupAuthGuard(router: Router) {
  router.beforeEach((to) => {
    const accessStore = useAccessStore();

    if (WHITE_LIST.includes(to.path)) {
      return true;
    }

    if (!accessStore.accessToken) {
      return {
        path: LOGIN_PATH,
        query: to.fullPath !== '/' ? { redirect: encodeURIComponent(to.fullPath) } : {},
        replace: true,
      };
    }

    return true;
  });
}

/**
 * 权限守卫：首次登录后根据权限动态加载路由
 */
function setupPermissionGuard(router: Router) {
  let routesAdded = false;

  router.beforeEach(async (to) => {
    const accessStore = useAccessStore();

    if (!accessStore.accessToken) {
      return true;
    }

    if (accessStore.isAccessChecked || routesAdded) {
      return true;
    }

    if (!routesAdded) {
      accessRoutes.forEach((route) => {
        router.addRoute('Root', route);
      });
      routesAdded = true;
    }

    if (to.name === 'FallbackNotFound') {
      return true;
    }

    return { ...to, replace: true };
  });
}

function createRouterGuard(router: Router) {
  setupCommonGuard(router);
  setupAuthGuard(router);
  setupPermissionGuard(router);
}

/**
 * 初始化动态路由
 * @description 将动态路由注册到路由实例中
 * @param router 路由实例
 */
function initRoutes(router: Router) {
  accessRoutes.forEach((route) => {
    router.addRoute('Root', route);
  });
}

export { createRouterGuard, initRoutes };
