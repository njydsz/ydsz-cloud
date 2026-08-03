/**
 * 子应用脚手架生成器。
 *
 * 一条命令从模板生成新的微应用，自动完成：
 *   - package.json（含 workspace 引用与 scripts）
 *   - vite.config.mts（micro-kernel manifest 插件 + ElementPlus + 端口配置）
 *   - tsconfig.json（继承 @ydsz/tsconfig）
 *   - src/main.ts / bootstrap.ts / App.vue（标准生命周期导出）
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

// ==================== 目录创建 ====================

const appDir = path.join(root, 'apps', name);
if (fs.existsSync(appDir)) {
  console.error(`应用 ${name} 已存在于 apps/${name}/`);
  process.exit(1);
}

fs.mkdirSync(path.join(appDir, 'src'), { recursive: true });
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
    '@ydsz/common-ui': 'workspace:*',
    '@ydsz/effects': 'workspace:*',
    '@ydsz/hooks': 'workspace:*',
    '@ydsz/icons': 'workspace:*',
    '@ydsz/locales': 'workspace:*',
    '@ydsz/preferences': 'workspace:*',
    '@ydsz/stores': 'workspace:*',
    '@ydsz/styles': 'workspace:*',
    '@ydsz/types': 'workspace:*',
    '@ydsz/utils': 'workspace:*',
    '@ydsz/micro-runtime': 'workspace:*',
    vue: 'catalog:',
    'vue-router': 'catalog:',
    pinia: 'catalog:',
    'element-plus': 'catalog:',
  },
  devDependencies: {
    '@ydsz/tsconfig': 'workspace:*',
    '@ydsz/vite-config': 'workspace:*',
    '@ydsz/tailwind-config': 'workspace:*',
    '@ydsz/micro-kernel': 'workspace:*',
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
      plugins: [
        ElementPlus({ format: 'esm' }),
      ],
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

// ==================== src/main.ts ====================

const mainTs = `import { createSubApp } from '@ydsz/effects/shared-auth';

/**
 * ${title} 子应用入口。
 *
 * 导出 micro-kernel 需要的标准生命周期：{ bootstrap, mount, unmount, update }。
 * micro-kernel 通过动态 import 加载此入口并调用 lifecycle 方法。
 *
 * @path apps/${name}/src/main.ts
 * @since 1.0.0
 */
const { bootstrap, mount, unmount, update } = createSubApp({
  async initApp(app) {
    // TODO: 注册全局组件/指令/插件
  },
});

export { bootstrap, mount, unmount, update };

// 独立运行（非微前端环境）
if (!import.meta.env.VITE_APP_NAMESPACE) {
  void bootstrap({ container: document.getElementById('app')!, basename: '${routePrefix}' });
}
`;

fs.writeFileSync(path.join(appDir, 'src', 'main.ts'), mainTs);

// ==================== src/App.vue ====================

const appVue = `<script setup lang="ts">
/**
 * ${title} 子应用根组件。
 *
 * @path apps/${name}/src/App.vue
 * @since 1.0.0
 */
</script>

<template>
  <div class="${name.replace(/-/g, '_')}">
    <h2>${title}</h2>
    <p>子应用模板，开始开发！</p>
  </div>
</template>
`;

fs.writeFileSync(path.join(appDir, 'src', 'App.vue'), appVue);

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
console.info(`  },`);
console.info(`\n然后运行 pnpm install 安装新包依赖。`);
