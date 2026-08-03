/**
 * Vite 插件：构建时生成 version.json 供 lite-kernel 加载。
 *
 * 约定：子应用必须输出 version.json（含 entry、css、版本号），
 * lite-kernel 通过 fetch version.json 获取入口信息，免去 HTML entry 解析。
 *
 * 在共享 vite-config 中作为可选插件引入。
 *
 * @path comm/effects/micro-kernel-lite/src/vite-plugin-manifest.ts
 * @author ydsz-team
 * @since 3.0.0
 */

import type { Plugin } from 'vite';

export interface ManifestPluginOptions {
  /** 子应用名称 */
  name: string;
  /** 子应用版本（建议取 package.json version + build hash） */
  version?: string;
}

export function viteManifestPlugin(options: ManifestPluginOptions): Plugin {
  const appName = options.name;
  const appVersion = options.version ?? '0.0.0';

  return {
    name: 'ydsz:micro-manifest',

    // 仅在 build 阶段启用
    apply: 'build',

    generateBundle(_options, bundle) {
      // 找到入口 chunk
      const entryChunk = Object.values(bundle).find(
        (chunk) => chunk.type === 'chunk' && chunk.isEntry,
      );

      if (!entryChunk || entryChunk.type !== 'chunk') {
        console.warn(`[ManifestPlugin] No entry chunk found for ${appName}`);
        return;
      }

      // 收集 CSS 文件
      const cssFiles = Object.values(bundle)
        .filter(
          (asset): asset is { type: 'asset'; fileName: string; source: string | Uint8Array } =>
            asset.type === 'asset' && asset.fileName.endsWith('.css'),
        )
        .map((asset) => `/${asset.fileName}`);

      const manifest = {
        name: appName,
        entry: `/${entryChunk.fileName}`,
        css: cssFiles,
        version: appVersion,
      };

      // 追加 version.json 到产物
      this.emitFile({
        type: 'asset',
        fileName: 'version.json',
        source: JSON.stringify(manifest),
      });

      console.info(`[ManifestPlugin] Generated manifest for ${appName}:`, manifest);
    },
  };
}
