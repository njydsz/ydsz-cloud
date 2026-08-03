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

/** Vite Manifest 插件配置项：子应用名称与可选版本号 */
export interface ManifestPluginOptions {
  /** 子应用名称 */
  name: string;
  /** 子应用版本（建议取 package.json version + build hash） */
  version?: string;
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

      const manifest = {
        name: appName,
        entry: `${base}${entryChunk.fileName}`,
        css: cssFiles,
        version: appVersion,
      };

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
