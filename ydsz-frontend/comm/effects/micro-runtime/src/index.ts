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
// v3.7.0: 命名空间 globalState 工厂
export { createNamespacedGlobalStateWrapper, createNamespacedState } from './namespaced-state';
export type { NamespacedGlobalStateAPI } from './namespaced-state';
// v4.0 P1-1: semver 版本校验工具
export { satisfiesVersion, parseVersion, compareVersion } from './semver';
export type { SemVer } from './semver';

// v4.0 P1-2: 主子应用标准化 Props 契约
export {
  buildStandardMountProps,
} from './standard-props';
export type {
  BuildPropsContext,
  EnhancedGlobalStateAPI,
  MessageBusAPI,
  MicroMessage,
  StandardMicroProps,
  SubAppContext,
} from './standard-props';

// v4.0 P1-2: 子应用侧标准化 Props 访问器
export {
  MICRO_PROPS_KEY,
  provideMicroProps,
  useGlobalState,
  useMessageBus,
  useMicroProps,
  useNamespace,
  useNamespaceState,
  useSubAppContext,
} from './use-micro-props';

// v3.7: 直接导出 MicroAppEntry 避免外部从 conf/vite-config 反向依赖
export type { MicroAppEntry } from './types';
