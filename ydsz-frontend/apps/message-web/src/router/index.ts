/**
 * 路由实例入口
 * <p>创建并导出 Vue Router 实例，初始化 history 模式（hash / web history）。
 * <p>注册路由守卫、动态路由、滚动行为等。
 * <p>供 {@code main.ts} 注入到 Vue App 使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import {
  createRouter,
  createWebHashHistory,
  createWebHistory,
} from 'vue-router';

import { resetStaticRoutes } from '@ydsz/utils';

import { createRouterGuard, initRoutes } from './guard';
import { routes } from './routes';

const router = createRouter({
  history:
    import.meta.env.VITE_ROUTER_HISTORY === 'hash'
      ? createWebHashHistory(import.meta.env.VITE_BASE)
      : createWebHistory(import.meta.env.VITE_BASE),
  routes,
  scrollBehavior: (to, _from, savedPosition) => {
    if (savedPosition) {
      return savedPosition;
    }
    return to.hash ? { behavior: 'smooth', el: to.hash } : { left: 0, top: 0 };
  },
});

const resetRoutes = () => resetStaticRoutes(router, routes);

initRoutes(router);

createRouterGuard(router);

export { resetRoutes, router };
