#!/usr/bin/env python3
"""P2-1 fix: Update core.ts for all 9 sub-apps (remove dead _core view imports)."""
import os

BASE = r"d:\Code\ydsz\ydsz-pmis\ydsz-frontend\apps"
APPS = ["userinfo-web", "system-web", "project-web", "message-web",
        "cronjob-web", "workflow-web", "nextwiki-web", "literule-web", "agent-web"]

CORE_TS = """\
import type { RouteRecordRaw } from 'vue-router';

import { preferences } from '@ydsz/preferences';

const BasicLayout = () => import('#/layouts/basic.vue');

/** 全局404页面 */
const fallbackNotFoundRoute: RouteRecordRaw = {
  component: () => import('#/views/fallback/not-found.vue'),
  meta: {
    hideInBreadcrumb: true,
    hideInMenu: true,
    hideInTab: true,
    title: '404',
  },
  name: 'FallbackNotFound',
  path: '/:path(.*)*',
};

/** 基本路由，这些路由是必须存在的 */
const coreRoutes: RouteRecordRaw[] = [
  {
    component: BasicLayout,
    meta: {
      hideInBreadcrumb: true,
      title: 'Root',
    },
    name: 'Root',
    path: '/',
    redirect: preferences.app.defaultHomePath,
    children: [],
  },
];

export { coreRoutes, fallbackNotFoundRoute };
"""

# Simple 404 fallback page (no dependency on deleted _core views)
NOT_FOUND_VUE = """\
<script lang="ts" setup>
import { useRouter } from 'vue-router';

const router = useRouter();
</script>

<template>
  <div class="flex h-full min-h-[400px] items-center justify-center">
    <div class="text-center">
      <div class="mb-4 text-6xl font-bold text-gray-300">404</div>
      <div class="mb-6 text-gray-500">页面不存在</div>
      <ElButton type="primary" @click="router.back()">返回上一页</ElButton>
    </div>
  </div>
</template>
"""

count = 0
for app in APPS:
    src = os.path.join(BASE, app, "src")

    # Update core.ts
    core_path = os.path.join(src, "router", "routes", "core.ts")
    with open(core_path, 'w', encoding='utf-8') as f:
        f.write(CORE_TS)
    count += 1

    # Create minimal fallback/not-found.vue
    fallback_dir = os.path.join(src, "views", "fallback")
    os.makedirs(fallback_dir, exist_ok=True)
    with open(os.path.join(fallback_dir, "not-found.vue"), 'w', encoding='utf-8') as f:
        f.write(NOT_FOUND_VUE)
    count += 1

    print(f"  {app}: core.ts + fallback/not-found.vue updated")

print(f"\nTotal: {count} files updated across {len(APPS)} sub-apps")
