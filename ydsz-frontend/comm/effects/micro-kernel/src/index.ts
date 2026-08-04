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
export { getVersionManager, resetVersionManager } from './version-manager';
export {
  evictAllKeepAliveOnMemoryPressure,
  getAllInstances,
  getAppInstance,
  getKeepAliveCount,
  setMaxKeepAliveApps,
} from './scheduler';
// v3.3: 公开 error-boundary i18n helpers，主应用可运行时切换降级 UI 文案语言
export {
  getErrorFallbackMessagesByLocale,
  setErrorFallbackMessages,
} from './error-boundary';
export type { ManifestPluginOptions, ManifestPluginRoute } from './vite-plugin-manifest';
export type { SandboxInstance, SandboxType } from './sandbox';
export type { ProxySandboxInstance } from './proxy-sandbox';
export type { VersionUpdateResult, VersionManagerOptions } from './version-manager';
export type { ErrorFallbackMessages } from './error-boundary';
// v3.3: 公开 Manifest 类型供主应用容器读取 routes 配置（骨架屏细化）
export type { LoadOptions, LoadResult, Manifest, ManifestRoute } from './loader';
