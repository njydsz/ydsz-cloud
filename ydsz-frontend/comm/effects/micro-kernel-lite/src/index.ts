/**
 * 统一导出 — @ydsz/micro-kernel-lite
 *
 * @path comm/effects/micro-kernel-lite/src/index.ts
 * @author ydsz-team
 * @since 3.0.0
 */

export { createLiteKernel } from './lite-kernel';
export { viteManifestPlugin } from './vite-plugin-manifest';
export { enterSandbox, exitSandbox } from './sandbox';
export type { ManifestPluginOptions } from './vite-plugin-manifest';
export type { SandboxInstance } from './sandbox';
