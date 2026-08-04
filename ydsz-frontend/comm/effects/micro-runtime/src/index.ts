/**
 * 统一导出 — @ydsz/micro-runtime
 *
 * @path comm/effects/micro-runtime/src/index.ts
 * @author ydsz-team
 * @since 3.0.0
 */
export * from './create-runtime';
export * from './global-state';
export * from './types';

export { provideGlobalState, useGlobalState, useGlobalStateRef } from './composable';

// v3.7: 直接导出 MicroAppEntry 避免外部从 conf/vite-config 反向依赖
export type { MicroAppEntry } from './types';
