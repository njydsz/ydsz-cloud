/**
 * 统一导出 — @ydsz/micro-kernel
 *
 * @path comm/effects/micro-kernel/src/index.ts
 * @author ydsz-team
 * @since 3.0.0
 */

export { createKernel } from './kernel';
export { viteManifestPlugin } from './vite-plugin-manifest';
export { enterSandbox, exitSandbox } from './sandbox';
export { createProxySandbox } from './proxy-sandbox';
export { createIframeSandbox } from './iframe-sandbox';
export { getVersionManager, resetVersionManager } from './version-manager';
// v3.5 (C4): 资源预连接与模块预加载提示
export {
  clearLinkHints,
  injectModulePreload,
  injectPreconnect,
  preloadAppAssets,
  preloadManifest,
} from './link-hints';
// v3.4: 公开预加载策略工厂，供主应用按需注册 frequency 策略
export {
  createFrequencyPreloadStrategy,
  createIdlePreloadStrategy,
  createHoverPreloadStrategy,
  createRoutePreloadStrategy,
  getPreloadManager,
  resetPreloadManager,
} from './preload-strategy';
export type {
  AppUsageStats,
  PermissionChecker,
  PreloadPriority,
  PreloadStrategy,
  PreloadStrategyOptions,
  PrefetchStrategy,
  PrefetchStrategyConfig,
} from './preload-strategy';
export {
  evictAllKeepAliveOnMemoryPressure,
  getAllInstances,
  getAppInstance,
  getKeepAliveCount,
  setMaxKeepAliveApps,
} from './scheduler';
// v3.6.0: 公开 GlobalStateBridge 类型，供外部扩展沙箱时使用
export type { GlobalStateBridge } from './scheduler';
// v3.3: 公开 error-boundary i18n helpers，主应用可运行时切换降级 UI 文案语言
export {
  getErrorFallbackMessagesByLocale,
  setErrorFallbackMessages,
} from './error-boundary';
export type { ManifestPluginOptions, ManifestPluginRoute } from './vite-plugin-manifest';
export type { SandboxInstance } from './sandbox';
export type { ProxySandboxInstance } from './proxy-sandbox';
export type { IframeSandboxInstance } from './iframe-sandbox';
// v3.6.0: SandboxType 单一事实源在 micro-runtime，scheduler re-export
export type { SandboxType } from './scheduler';
export type { VersionUpdateResult, VersionManagerOptions } from './version-manager';
export type { ErrorFallbackMessages } from './error-boundary';
// v3.3: 公开 Manifest 类型供主应用容器读取 routes 配置（骨架屏细化）
export type { LoadOptions, LoadResult, Manifest, ManifestRoute } from './loader';
