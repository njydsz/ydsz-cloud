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
export { getVersionManager, resetVersionManager } from './version-manager';
export type { ManifestPluginOptions } from './vite-plugin-manifest';
export type { SandboxInstance } from './sandbox';
export type { VersionUpdateResult, VersionManagerOptions } from './version-manager';
