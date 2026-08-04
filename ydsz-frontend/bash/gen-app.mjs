/**
 * 子应用脚手架生成器。
 *
 * 一条命令从模板生成新的微应用，自动完成：
 *   - package.json（含 workspace 引用与 scripts，对齐现行子应用依赖）
 *   - vite.config.mts（ElementPlus + 端口配置）
 *   - tsconfig.json（继承 @ydsz/tsconfig）
 *   - src/main.ts / app.vue（标准生命周期导出，createSubApp 工厂模式）
 *   - src/preferences.ts / adapter / store / router / locales 骨架
 *   - .env / .env.development / .env.production / .env.analyze
 *   - postcss.config.mjs / tailwind.config.mjs
 *   - index.html
 *   - 注册表 MICRO_APPS 追加新条目提示
 *
 * 使用方式：node bash/gen-app.mjs <app-name> <title> <route-prefix> [port]
 *
 * @example
 *   pnpm gen:app report-web 数据报表 /ydsz-report 5611
 *
 * @path bash/gen-app.mjs
 * @author ydsz-team
 * @since 3.0.0
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');

// ==================== 参数解析 ====================

const [name, title, routePrefix, portStr] = process.argv.slice(2);

if (!name || !title || !routePrefix) {
  console.error('用法: pnpm gen:app <app-name> <title> <route-prefix> [port]');
  console.error('示例: pnpm gen:app report-web 数据报表 /ydsz-report 5611');
  process.exit(1);
}

const port = Number.parseInt(portStr || '5611', 10);
const packageName = `@ydsz/${name}`;
const namespace = name.replace(/-web$/, '').replace(/-/g, '-');
const appTitleEn = title;

// ==================== 目录创建 ====================

const appDir = path.join(root, 'apps', name);
if (fs.existsSync(appDir)) {
  console.error(`应用 ${name} 已存在于 apps/${name}/`);
  process.exit(1);
}

const dirs = ['src', 'src/adapter', 'src/api/core', 'src/locales/langs/zh-CN', 'src/locales/langs/en-US', 'src/router/routes/modules', 'src/store', 'src/views'];
for (const d of dirs) {
  fs.mkdirSync(path.join(appDir, d), { recursive: true });
}
console.info(`[GenApp] Creating ${name} in apps/${name}/`);

// ==================== package.json ====================

const pkgJson = {
  name: packageName,
  version: '1.0.0',
  private: true,
  type: 'module',
  scripts: {
    dev: `vite --port ${port}`,
    build: 'vite build',
    preview: 'vite preview',
  },
  dependencies: {
    '@ydsz/access': 'workspace:*',
    '@ydsz/common-ui': 'workspace:*',
    '@ydsz/constants': 'workspace:*',
    '@ydsz/hooks': 'workspace:*',
    '@ydsz/icons': 'workspace:*',
    '@ydsz/layouts': 'workspace:*',
    '@ydsz/locales': 'workspace:*',
    '@ydsz/monitor': 'workspace:*',
    '@ydsz/plugins': 'workspace:*',
    '@ydsz/preferences': 'workspace:*',
    '@ydsz/request': 'workspace:*',
    '@ydsz/shared-auth': 'workspace:*',
    '@ydsz/shared-business': 'workspace:*',
    '@ydsz/stores': 'workspace:*',
    '@ydsz/styles': 'workspace:*',
    '@ydsz/types': 'workspace:*',
    '@ydsz/utils': 'workspace:*',
    '@vueuse/core': 'catalog:',
    dayjs: 'catalog:',
    'element-plus': 'catalog:',
    pinia: 'catalog:',
    vue: 'catalog:',
    'vue-router': 'catalog:',
  },
  devDependencies: {
    '@ydsz/tsconfig': 'workspace:*',
    '@ydsz/vite-config': 'workspace:*',
    '@ydsz/tailwind-config': 'workspace:*',
    typescript: 'catalog:',
    vite: 'catalog:',
    'unplugin-element-plus': 'catalog:',
  },
};

fs.writeFileSync(
  path.join(appDir, 'package.json'),
  JSON.stringify(pkgJson, null, 2) + '\n',
);

// ==================== vite.config.mts ====================

const viteConfig = `import { defineConfig } from '@ydsz/vite-config';
import ElementPlus from 'unplugin-element-plus/vite';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      base: '/',
      plugins: [ElementPlus({ format: 'esm' })],
      server: {
        port: ${port},
        cors: true,
        host: '0.0.0.0',
        headers: { 'Access-Control-Allow-Origin': '*' },
        proxy: {
          '/api': {
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\\/api/, ''),
            target: 'http://localhost:9000',
            ws: true,
          },
        },
      },
    },
  };
});
`;

fs.writeFileSync(path.join(appDir, 'vite.config.mts'), viteConfig);

// ==================== tsconfig.json ====================

const tsconfig = {
  $schema: 'https://json.schemastore.org/tsconfig',
  extends: '@ydsz/tsconfig/web-app.json',
  compilerOptions: {
    composite: true,
    baseUrl: '.',
    paths: {
      '#/*': ['./src/*'],
    },
  },
  include: ['src/**/*.ts', 'src/**/*.tsx', 'src/**/*.vue'],
};

fs.writeFileSync(
  path.join(appDir, 'tsconfig.json'),
  JSON.stringify(tsconfig, null, 2) + '\n',
);

// ==================== index.html ====================

const indexHtml = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <link rel="icon" href="/favicon.ico" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>${title}</title>
</head>
<body>
  <div id="app"></div>
  <script type="module" src="/src/main.ts"></script>
</body>
</html>
`;

fs.writeFileSync(path.join(appDir, 'index.html'), indexHtml);

// ==================== .env ====================

const envBase = `VITE_APP_TITLE=${title}
VITE_APP_NAMESPACE=ydsz-${namespace}
VITE_APP_VERSION=1.0.0
VITE_APP_STORE_SECURE_KEY=ydsz-pmis-2026-secure-key
`;

const envDev = `VITE_PORT=${port}
VITE_BASE=/
VITE_GLOB_API_URL=/api
VITE_DEVTOOLS=false
VITE_INJECT_APP_LOADING=true
`;

const envProd = `VITE_BASE=/${name}/
VITE_GLOB_API_URL=/api
VITE_COMPRESS=gzip,brotli
VITE_PWA=false
VITE_ROUTER_HISTORY=history
VITE_INJECT_APP_LOADING=true
VITE_ARCHIVER=true
`;

const envAnalyze = `VITE_BASE=/
VITE_GLOB_API_URL=/api
VITE_VISUALIZER=true
`;

fs.writeFileSync(path.join(appDir, '.env'), envBase);
fs.writeFileSync(path.join(appDir, '.env.development'), envDev);
fs.writeFileSync(path.join(appDir, '.env.production'), envProd);
fs.writeFileSync(path.join(appDir, '.env.analyze'), envAnalyze);

// ==================== postcss.config.mjs / tailwind.config.mjs ====================

fs.writeFileSync(
  path.join(appDir, 'postcss.config.mjs'),
  `export { default } from '@ydsz/tailwind-config/postcss';\n`,
);

fs.writeFileSync(
  path.join(appDir, 'tailwind.config.mjs'),
  `export { default } from '@ydsz/tailwind-config';\n`,
);

// ==================== src/main.ts ====================

const mainTs = `import { createSubApp } from '@ydsz/shared-auth';

import '@ydsz/styles';
import '@ydsz/styles/ele';

import { initComponentAdapter } from './adapter/component';
import { initSetupYDSZForm } from './adapter/form';
import RootApp from './app.vue';
import { setupI18n } from './locales';
import { overridesPreferences } from './preferences';
import { createRouterGuard, initRoutes } from './router/guard';
import { routes } from './router/routes';

/**
 * ${title} 子应用入口。
 *
 * 导出 micro-kernel 需要的标准生命周期：{ bootstrap, mount, unmount, update }。
 * micro-kernel 通过动态 import 加载此入口并调用 lifecycle 方法。
 *
 * @path apps/${name}/src/main.ts
 * @since 1.0.0
 */
export const { bootstrap, mount, unmount, update } = createSubApp({
  appName: '${name}',
  basename: '${routePrefix}',
  routes,
  rootComponent: RootApp,
  preferencesOverrides: overridesPreferences,
  initRoutes,
  guard: createRouterGuard,
  async onSetup(app) {
    await initComponentAdapter();
    await initSetupYDSZForm();
    await setupI18n(app);
  },
});
`;

fs.writeFileSync(path.join(appDir, 'src', 'main.ts'), mainTs);

// ==================== src/app.vue ====================

const appVue = `<script lang="ts" setup>
import { useElementPlusDesignTokens } from '@ydsz/hooks';
import { ElConfigProvider } from 'element-plus';

import { elementLocale } from '#/locales';

defineOptions({ name: 'App' });

useElementPlusDesignTokens();
</script>

<template>
  <ElConfigProvider :locale="elementLocale">
    <RouterView />
  </ElConfigProvider>
</template>
`;

fs.writeFileSync(path.join(appDir, 'src', 'app.vue'), appVue);

// ==================== src/preferences.ts ====================

const preferencesTs = `import { defineOverridesPreferences } from '@ydsz/preferences';

/**
 * ${title} 子应用偏好覆盖。
 *
 * 子应用作为内容区嵌入基座，隐藏侧边栏，固定主题。
 *
 * @path apps/${name}/src/preferences.ts
 * @since 1.0.0
 */
export const overridesPreferences = defineOverridesPreferences({
  app: {
    defaultHomePath: '${routePrefix}',
    name: '${appTitleEn}',
  },
  theme: {
    mode: 'light',
    builtinType: 'deep-blue',
    colorPrimary: 'hsl(211 98% 52%)',
  },
  sidebar: {
    hidden: true,
  },
});
`;

fs.writeFileSync(path.join(appDir, 'src', 'preferences.ts'), preferencesTs);

// ==================== src/adapter/component/index.ts ====================

const adapterComponentTs = `import { registerElementPlusComponents } from '@ydsz/shared-auth';

import type { ComponentType } from './component-type';

/**
 * 初始化组件适配器：注册 Element Plus 组件到全局共享状态。
 *
 * @path apps/${name}/src/adapter/component/index.ts
 * @since 1.0.0
 */
export async function initComponentAdapter(): Promise<void> {
  await registerElementPlusComponents<ComponentType>();
}

export type { ComponentType };
`;

fs.mkdirSync(path.join(appDir, 'src', 'adapter', 'component'), { recursive: true });
fs.writeFileSync(path.join(appDir, 'src', 'adapter', 'component', 'index.ts'), adapterComponentTs);

// ==================== src/adapter/form.ts ====================

const adapterFormTs = `import { createSetupYDSZForm } from '@ydsz/shared-auth';

import type { ComponentType } from './component';

/**
 * 表单适配器：绑定组件类型映射与全局校验规则。
 *
 * @path apps/${name}/src/adapter/form.ts
 * @since 1.0.0
 */
export const { useYDSZForm, z, YDSZFormSchema } = createSetupYDSZForm<ComponentType>();

export async function initSetupYDSZForm(): Promise<void> {
  // 组件类型映射与校验规则已在 createSetupYDSZForm 中完成
}
`;

fs.writeFileSync(path.join(appDir, 'src', 'adapter', 'form.ts'), adapterFormTs);

// ==================== src/api/request.ts ====================

const apiRequestTs = `export {
  baseRequestClient,
  initSharedRequest,
  requestClient,
} from '@ydsz/shared-auth';
`;

fs.writeFileSync(path.join(appDir, 'src', 'api', 'request.ts'), apiRequestTs);

// ==================== src/api/index.ts ====================

const apiIndexTs = `export * from './core';
export { requestClient, baseRequestClient } from './request';
`;

fs.writeFileSync(path.join(appDir, 'src', 'api', 'index.ts'), apiIndexTs);

// ==================== src/api/core/index.ts ====================

const apiCoreTs = `export * from '@ydsz/shared-auth/auth-api';
`;

fs.writeFileSync(path.join(appDir, 'src', 'api', 'core', 'index.ts'), apiCoreTs);

// ==================== src/store/auth.ts ====================

const storeAuthTs = `export { createSharedAuthStore } from '@ydsz/shared-auth';

export const useAuthStore = createSharedAuthStore();
`;

fs.writeFileSync(path.join(appDir, 'src', 'store', 'auth.ts'), storeAuthTs);

// ==================== src/store/index.ts ====================

const storeIndexTs = `export * from './auth';
`;

fs.writeFileSync(path.join(appDir, 'src', 'store', 'index.ts'), storeIndexTs);

// ==================== src/router/guard.ts ====================

const routerGuardTs = `import type { Router } from 'vue-router';

import { createSubAppRouterGuard, initRoutes as sharedInitRoutes } from '@ydsz/shared-auth/guards';

import { accessRoutes } from '#/router/routes';

function createRouterGuard(router: Router) {
  createSubAppRouterGuard(router, accessRoutes);
}

function initRoutes(router: Router) {
  sharedInitRoutes(router, accessRoutes);
}

export { createRouterGuard, initRoutes };
`;

fs.writeFileSync(path.join(appDir, 'src', 'router', 'guard.ts'), routerGuardTs);

// ==================== src/router/index.ts ====================

const routerIndexTs = `import type { RouteRecordRaw } from 'vue-router';

import { createRouter, createWebHistory } from 'vue-router';

import { createRouterGuard, initRoutes } from './guard';
import { routes } from './routes';

const router = createRouter({
  history: createWebHistory(import.meta.env.VITE_BASE),
  routes: routes as RouteRecordRaw[],
  scrollBehavior: () => ({ left: 0, top: 0 }),
});

initRoutes(router);
createRouterGuard(router);

export { router };
`;

fs.writeFileSync(path.join(appDir, 'src', 'router', 'index.ts'), routerIndexTs);

// ==================== src/router/routes/index.ts ====================

const routesIndexTs = `import type { RouteRecordRaw } from 'vue-router';

import { mergeRouteModules } from '@ydsz/utils';

import { coreRoutes, fallbackNotFoundRoute } from './core';

const modules = import.meta.glob('./modules/**/*.ts', { eager: true }) as Record<
  string,
  { default: RouteRecordRaw[] }
>;

const dynamicRoutes = mergeRouteModules(modules);

export const accessRoutes = [...coreRoutes, ...dynamicRoutes];

export const routes = [...accessRoutes, fallbackNotFoundRoute];
`;

fs.writeFileSync(path.join(appDir, 'src', 'router', 'routes', 'index.ts'), routesIndexTs);

// ==================== src/router/routes/core.ts ====================

const routesCoreTs = `import type { RouteRecordRaw } from 'vue-router';

export const coreRoutes: RouteRecordRaw[] = [
  {
    component: () => import('#/views/index.vue'),
    meta: { skeletonType: 'default', title: '${title}' },
    name: 'Root',
    path: '${routePrefix}',
    redirect: '${routePrefix}/',
  },
];

export const fallbackNotFoundRoute: RouteRecordRaw = {
  component: () => import('#/views/fallback/not-found.vue'),
  meta: { hideInMenu: true, title: 'Fallback' },
  name: 'FallbackNotFound',
  path: '/:pathMatch(.*)*',
};
`;

fs.writeFileSync(path.join(appDir, 'src', 'router', 'routes', 'core.ts'), routesCoreTs);

// ==================== src/locales/index.ts ====================

const localesIndexTs = `/**
 * 国际化配置入口 — 通过 @ydsz/shared-auth 的 createSubAppI18n 工厂装配。
 *
 * @path apps/${name}/src/locales/index.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { createSubAppI18n } from '@ydsz/shared-auth';

const modules = import.meta.glob('./langs/**/*.json');

export const { $t, elementLocale, setupI18n } = createSubAppI18n({ modules });
`;

fs.writeFileSync(path.join(appDir, 'src', 'locales', 'index.ts'), localesIndexTs);

// ==================== src/locales/langs/zh-CN/page.json ====================

const zhPageJson = JSON.stringify({ page: { title: '${title}' } }, null, 2) + '\n';
fs.writeFileSync(path.join(appDir, 'src', 'locales', 'langs', 'zh-CN', 'page.json'), zhPageJson);

const enPageJson = JSON.stringify({ page: { title: '${appTitleEn}' } }, null, 2) + '\n';
fs.writeFileSync(path.join(appDir, 'src', 'locales', 'langs', 'en-US', 'page.json'), enPageJson);

// ==================== src/views/index.vue ====================

const viewsIndexVue = `<script lang="ts" setup>

defineOptions({ name: '${appTitleEn.replace(/[^a-zA-Z]/g, '')}Home' });
</script>

<template>
  <div class="${name.replace(/-/g, '_')}_home">
    <h2>${title}</h2>
    <p>子应用模板，开始开发！</p>
  </div>
</template>
`;

fs.writeFileSync(path.join(appDir, 'src', 'views', 'index.vue'), viewsIndexVue);

// ==================== src/views/fallback/not-found.vue ====================

fs.mkdirSync(path.join(appDir, 'src', 'views', 'fallback'), { recursive: true });
const notFoundVue = `<script lang="ts" setup>
defineOptions({ name: 'FallbackNotFound' });
</script>

<template>
  <div class="flex h-full items-center justify-center">
    <span class="text-lg">页面不存在</span>
  </div>
</template>
`;
fs.writeFileSync(path.join(appDir, 'src', 'views', 'fallback', 'not-found.vue'), notFoundVue);

// ==================== 注册表提示 ====================

console.info(`\n✅ 子应用 ${name} 已生成！`);
console.info(`\n请手动在 conf/vite-config/src/micro-apps.config.ts 的 MICRO_APPS 数组中追加：`);
console.info(`\n  {`);
console.info(`    name: '${name}',`);
console.info(`    packageName: '${packageName}',`);
console.info(`    activeRule: '${routePrefix}',`);
console.info(`    redirect: '${routePrefix}/',`);
console.info(`    title: '${title}',`);
console.info(`    icon: 'lucide:box',  // TODO: 选择合适的 lucide 图标`);
console.info(`    order: 109,  // TODO: 调整排序权重`);
console.info(`    devPort: ${port},`);
console.info(`    skeletonType: 'default',  // 可选: dashboard | default | detail | form | list`);
console.info(`  },`);
console.info(`\n然后运行 pnpm install 安装新包依赖。`);
console.info(`\n完成注册表追加后，请同步 nginx 配置：`);
console.info(`  pnpm run gen:nginx`);
