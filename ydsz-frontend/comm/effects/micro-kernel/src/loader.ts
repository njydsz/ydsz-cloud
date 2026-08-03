/**
 * ESM Manifest 加载器
 *
 * 约定：子应用由统一 vite-config 构建，输出 manifest.json：
 *   { "name": "project-web", "entry": "...", "css": [...], "version": "..." }
 *
 * 加载流程：
 *   build 模式：fetch manifest → 注入 CSS → dynamic import ESM entry → 断言生命周期。
 *   dev 模式（M4 修复）：跳过 manifest，直接 dynamic import 子应用 dev server 入口。
 *
 * @path comm/effects/micro-kernel/src/loader.ts
 * @author ydsz-team
 * @since 3.0.0
 */

import type { LifecycleExports, MicroAppConfig } from '@ydsz/micro-runtime';
import { createLogger } from '@ydsz-core/shared/utils';

/** 模块级日志器（重试等运维信息走 debug，避免生产噪音） */
const logger = createLogger('MicroKernel');

/** 子应用构建产出的 manifest.json 结构：应用名、入口、样式表列表与版本号 */
export interface Manifest {
  name: string;
  entry: string;
  css: string[];
  version: string;
}

/** 加载配置 */
export interface LoadOptions {
  /** 加载超时（毫秒），默认 10_000 */
  timeout?: number;
  /** 失败重试次数，默认 2 次（总共 1+2=3 次尝试） */
  retries?: number;
  /** 重试延迟基数（毫秒），指数退避：delay = base * 2^(n-1) */
  retryBaseDelay?: number;
  /** 外部 AbortSignal（叠加于超时之上） */
  signal?: AbortSignal;
}

/** 加载结果 */
export interface LoadResult {
  exports: LifecycleExports;
  manifest: null | Manifest;
  /** 加载耗时（毫秒） */
  duration: number;
  /** 是否来自缓存（dev 模式固定 false） */
  fromCache: boolean;
}

const manifestCache = new Map<string, Manifest>();

/** 获取是否为开发模式 */
const isDev = typeof import.meta !== 'undefined' && (import.meta as { env?: Record<string, unknown> }).env?.DEV === true;

/**
 * 通过 fetch manifest.json 获取子应用入口信息。
 * Dev 模式下不调用（manifest.json 仅在 build 产物的 dist 中）。
 */
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
      `[MicroKernel] Failed to fetch manifest from ${manifestUrl}: ${response.status}`,
    );
  }

  const manifest: Manifest = await response.json();
  manifestCache.set(manifestUrl, manifest);
  return manifest;
}

/**
 * 加载子应用 ESM 入口。
 *
 * 相比 qiankun import-html-entry：无 HTML 解析、无 UMD、无 eval。
 *
 * @param config - 子应用注册配置
 * @param options - 加载选项（超时、重试）
 * @returns 标准 LifecycleExports + manifest + 耗时
 */
export async function loadApp(
  config: MicroAppConfig,
  options: LoadOptions = {},
): Promise<LoadResult> {
  const { timeout = 10_000, retries = 2, retryBaseDelay = 500, signal: extSignal } = options;
  const startTime = performance.now();

  // === M4 修复：Dev 模式直接 import 子应用 dev server 入口，跳过 manifest ===
  if (isDev) {
    const entryUrl = `${config.entry.replace(/\/$/, '')}/src/main.ts`;
    const mod = await importWithRetry(entryUrl, { timeout, retries, retryBaseDelay, extSignal });
    assertLifecycle(mod, config.name);
    return {
      exports: mod as LifecycleExports,
      manifest: null,
      duration: performance.now() - startTime,
      fromCache: false,
    };
  }

  // === Build 模式：fetch manifest → 注入 CSS → dynamic import ESM entry ===
  const manifest = await fetchWithRetry(
    () => fetchManifest(config.entry, extSignal),
    { timeout, retries, retryBaseDelay },
    `manifest for ${config.name}`,
  );

  // 注入样式（标记 data-micro-kernel-app，卸载时一键移除）
  injectStylesheets(manifest.css, manifest.name);

  const mod = await importWithRetry(manifest.entry, { timeout, retries, retryBaseDelay, extSignal });
  assertLifecycle(mod, config.name);

  return {
    exports: mod as LifecycleExports,
    manifest,
    duration: performance.now() - startTime,
    fromCache: manifestCache.has(`${config.entry.replace(/\/$/, '')}/manifest.json`),
  };
}

/**
 * 带超时与指数退避重试的 dynamic import。
 *
 * @param url - ESM 模块 URL
 * @param opts - 超时/重试配置
 * @returns 导入的模块对象
 */
async function importWithRetry(
  url: string,
  opts: { timeout: number; retries: number; retryBaseDelay: number; extSignal?: AbortSignal },
): Promise<Record<string, unknown>> {
  let lastError: unknown;

  for (let attempt = 0; attempt <= opts.retries; attempt++) {
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(`Load timeout: ${url}`), opts.timeout);

      // 外部 signal 如取消，也 abort 内部控制器
      const onExtAbort = (): void => controller.abort('Aborted externally');
      opts.extSignal?.addEventListener('abort', onExtAbort, { once: true });

      try {
        const mod = await import(/* @vite-ignore */ url);
        return mod;
      } finally {
        clearTimeout(timeoutId);
        opts.extSignal?.removeEventListener('abort', onExtAbort);
      }
    } catch (err) {
      lastError = err;
      if (attempt < opts.retries) {
        const delay = opts.retryBaseDelay * 2 ** attempt;
        logger.debug(
          `Import failed (attempt ${attempt + 1}/${opts.retries + 1}): ${url}. Retrying in ${delay}ms...`,
        );
        await new Promise((r) => setTimeout(r, delay));
      }
    }
  }

  throw lastError;
}

/**
 * 带重试的 fetch 包装。
 */
async function fetchWithRetry<T>(
  fn: () => Promise<T>,
  opts: { timeout: number; retries: number; retryBaseDelay: number },
  label: string,
): Promise<T> {
  let lastError: unknown;

  for (let attempt = 0; attempt <= opts.retries; attempt++) {
    try {
      return await fn();
    } catch (err) {
      lastError = err;
      if (attempt < opts.retries) {
        const delay = opts.retryBaseDelay * 2 ** attempt;
        logger.debug(
          `Fetch failed (attempt ${attempt + 1}/${opts.retries + 1}): ${label}. Retrying in ${delay}ms...`,
        );
        await new Promise((r) => setTimeout(r, delay));
      }
    }
  }

  throw lastError;
}

/** 注入样式表，并标记 data-micro-kernel-app="name" 以便卸载时移除 */
function injectStylesheets(cssUrls: string[], appName: string): void {
  for (const href of cssUrls) {
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = href;
    link.setAttribute('data-micro-kernel-app', appName);
    document.head.appendChild(link);
  }
}

/** 移除指定应用注入的样式表 */
export function removeStylesheets(appName: string): void {
  const links = document.querySelectorAll(`link[data-micro-kernel-app="${appName}"]`);
  for (const link of links) {
    link.remove();
  }
}

/** 断言模块导出 mount 方法（必需）和 unmount（必需） */
function assertLifecycle(module: Record<string, unknown>, appName: string): void {
  if (typeof module.mount !== 'function') {
    throw new Error(
      `[MicroKernel] App "${appName}" must export "mount" function. Found: ${Object.keys(module).join(', ')}`,
    );
  }
  if (typeof module.unmount !== 'function') {
    throw new Error(
      `[MicroKernel] App "${appName}" must export "unmount" function. Found: ${Object.keys(module).join(', ')}`,
    );
  }
}
