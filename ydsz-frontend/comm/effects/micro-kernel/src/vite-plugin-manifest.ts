/**
 * Vite 插件：构建时生成 manifest.json 供 micro-kernel 加载。
 *
 * 约定：子应用必须输出 manifest.json（含 entry、css、版本号），
 * micro-kernel 通过 fetch manifest.json 获取入口信息，免去 HTML entry 解析。
 *
 * 路径处理：使用 Vite 配置的 base 前缀拼接，确保在子路径部署
 * （如 /ydsz-project-web/）下 entry/css 路径正确，不再硬编码 `/` 根路径。
 *
 * 在共享 vite-config 中作为可选插件引入。
 *
 * @path comm/effects/micro-kernel/src/vite-plugin-manifest.ts
 * @author ydsz-team
 * @since 3.0.0
 */

import type { Plugin } from 'vite';

/** 子应用 manifest.json 中声明的路由级骨架屏配置（与 loader.ts ManifestRoute 对齐） */
export interface ManifestPluginRoute {
  /** 路由前缀（相对于子应用 basename 的子路径，如 '/users'、'/detail'） */
  path: string;
  /** 骨架屏类型（list/form/detail/dashboard/default） */
  skeletonType?: string;
}

/** Vite Manifest 插件配置项：子应用名称与可选版本号 */
export interface ManifestPluginOptions {
  /** 子应用名称 */
  name: string;
  /** 子应用版本（建议取 package.json version + build hash） */
  version?: string;
  /**
   * 路由级骨架屏配置（v3.3 新增，可选）。
   *
   * 子应用按自身路由前缀声明骨架屏类型，构建期写入 manifest.json，
   * 主应用容器加载 manifest 后据此匹配当前路由子路径，渲染对应骨架屏。
   *
   * @example
   *   routes: [
   *     { path: '/users', skeletonType: 'list' },
   *     { path: '/dashboard', skeletonType: 'dashboard' },
   *     { path: '/form', skeletonType: 'form' },
   *   ]
   */
  routes?: ManifestPluginRoute[];
}

/** 创建构建期生成 manifest.json 的 Vite 插件，供 micro-kernel 加载子应用入口与样式 */
export function viteManifestPlugin(options: ManifestPluginOptions): Plugin {
  const appName = options.name;
  const appVersion = options.version ?? '0.0.0';
  let base = '/';

  return {
    name: 'ydsz:micro-manifest',

    // 仅在 build 阶段启用
    apply: 'build',

    /** 捕获 Vite 解析后的 base 配置（如 /ydsz-project-web/），供路径拼接使用 */
    configResolved(config) {
      base = config.base;
    },

    generateBundle(_options, bundle) {
      // 找到入口 chunk
      const entryChunk = Object.values(bundle).find(
        (chunk) => chunk.type === 'chunk' && chunk.isEntry,
      );

      if (!entryChunk || entryChunk.type !== 'chunk') {
        console.warn(`[ManifestPlugin] No entry chunk found for ${appName}`);
        return;
      }

      // 收集 CSS 文件，使用 base 前缀确保子路径部署正确
      const cssFiles = Object.values(bundle)
        .filter(
          (asset): asset is { type: 'asset'; fileName: string; source: string | Uint8Array } =>
            asset.type === 'asset' && asset.fileName.endsWith('.css'),
        )
        .map((asset) => `${base}${asset.fileName}`);

      const manifest: Record<string, unknown> = {
        name: appName,
        entry: `${base}${entryChunk.fileName}`,
        css: cssFiles,
        version: appVersion,
      };

      // v3.3: 透传路由级骨架屏配置（可选）
      if (Array.isArray(options.routes) && options.routes.length > 0) {
        manifest.routes = options.routes;
      }

      // 追加 manifest.json 到产物（loader.ts 对应 fetch manifest.json）
      this.emitFile({
        type: 'asset',
        fileName: 'manifest.json',
        source: JSON.stringify(manifest),
      });

      console.info(`[ManifestPlugin] Generated manifest for ${appName}:`, manifest);
    },
  };
}
