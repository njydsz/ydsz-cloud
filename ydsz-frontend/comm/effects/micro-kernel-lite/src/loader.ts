/**
 * ESM Manifest 加载器
 *
 * 约定：子应用由统一 vite-config 构建，输出 manifest.json：
 *   { "name": "project-web", "entry": "...", "css": [...], "version": "..." }
 *
 * 加载流程：fetch manifest → 注入 CSS scoped → dynamic import ESM entry → 断言生命周期。
 *
 * @path comm/effects/micro-kernel-lite/src/loader.ts
 * @author ydsz-team
 * @since 3.0.0
 */

import type { LifecycleExports, MicroAppConfig } from '@ydsz/micro-runtime';

export interface Manifest {
  name: string;
  entry: string;
  css: string[];
  version: string;
}

const manifestCache = new Map<string, Manifest>();

/** 通过 fetch manifest.json 获取子应用入口信息 */
export async function fetchManifest(
  entry: string,
  signal?: AbortSignal,
): Promise<Manifest> {
  const manifestUrl = `${entry.replace(/\/$/, '')}/manifest.json`;

  if (manifestCache.has(manifestUrl)) {
    return manifestCache.get(manifestUrl)!;
  }

  const response = await fetch(manifestUrl, { signal });
  if (!response.ok) {
    throw new Error(
      `[LiteKernel] Failed to fetch manifest from ${manifestUrl}: ${response.status}`,
    );
  }

  const manifest: Manifest = await response.json();
  manifestCache.set(manifestUrl, manifest);
  return manifest;
}

/**
 * 加载子应用 ESM 入口。
 *
 * 返回标准 LifecycleExports（mount/unmount 等）。
 * 相比 qiankun import-html-entry：无 HTML 解析、无 UMD、无 eval。
 * Dev 模式下 entry 直接指向 vite dev server 的 src/main.ts。
 */
export async function loadApp(
  config: MicroAppConfig,
  signal?: AbortSignal,
): Promise<{ exports: LifecycleExports; manifest: Manifest }> {
  const manifest = await fetchManifest(config.entry, signal);

  // 1. 注入样式（scoped 前缀 + data 属性标记，卸载时一键移除）
  injectStylesheets(manifest.css, manifest.name);

  // 2. 动态加载 ESM 模块
  const module = await import(/* @vite-ignore */ manifest.entry);

  // 3. 断言生命周期导出
  assertLifecycle(module, config.name);

  return {
    exports: module as LifecycleExports,
    manifest,
  };
}

/** 注入样式表，并标记 data-lite-kernel-app="name" 以便卸载时移除 */
function injectStylesheets(cssUrls: string[], appName: string): void {
  for (const href of cssUrls) {
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = href;
    link.setAttribute('data-lite-kernel-app', appName);
    document.head.appendChild(link);
  }
}

/** 移除指定应用注入的样式表 */
export function removeStylesheets(appName: string): void {
  const links = document.querySelectorAll(`link[data-lite-kernel-app="${appName}"]`);
  for (const link of links) {
    link.remove();
  }
}

/** 断言模块导出 mount 方法（必需）和 unmount（必需） */
function assertLifecycle(module: Record<string, unknown>, appName: string): void {
  if (typeof module.mount !== 'function') {
    throw new Error(
      `[LiteKernel] App "${appName}" must export "mount" function. Found: ${Object.keys(module).join(', ')}`,
    );
  }
  if (typeof module.unmount !== 'function') {
    throw new Error(
      `[LiteKernel] App "${appName}" must export "unmount" function. Found: ${Object.keys(module).join(', ')}`,
    );
  }
}
