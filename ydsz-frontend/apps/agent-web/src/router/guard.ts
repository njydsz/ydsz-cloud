/**
 * 路由守卫 — 统一通过 shared-auth 包提供的标准化守卫注册。
 *
 * 子应用无需重复实现 commonGuard / authGuard / permissionGuard / initRoutes，
 * 直接传入本应用的 accessRoutes 即可。
 *
 * @author ydsz-team
 * @since 2.0.0
 */
import type { Router } from "vue-router";

import {
  createSubAppRouterGuard,
  initRoutes as sharedInitRoutes,
} from "@ydsz/shared-auth/guards";

import { accessRoutes } from "#/router/routes";

function createRouterGuard(router: Router) {
  createSubAppRouterGuard(router, accessRoutes);
}

function initRoutes(router: Router) {
  sharedInitRoutes(router, accessRoutes);
}

export { createRouterGuard, initRoutes };

