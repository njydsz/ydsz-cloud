/**
 * Vue 组合式 API — 子应用侧使用全局状态
 *
 * 子应用无需关心底层是 qiankun 还是 micro-kernel，
 * 始终通过 useGlobalState 获取类型化的响应式状态。
 *
 * v3.2 修复：原实现 subscribe 返回的 unsubscribe 未在组件卸载时调用，
 *          导致长生命周期子应用累积订阅泄漏；同时移除无意义的
 *          `watch(value, () => {}, { flush: 'sync' })` 死代码。
 *          改用 onScopeDispose 在组件/effect scope 销毁时统一清理。
 *
 * @path comm/effects/micro-runtime/src/composable/use-global-state.ts
 * @author ydsz-team
 * @since 3.0.0
 */

import type { Ref } from 'vue';
import { computed, onScopeDispose, ref } from 'vue';
import type { GlobalStateHandle } from '../global-state';

/** 全局状态单例（由主应用/子应用 bootstrap 时注入） */
let globalStateHandle: null | GlobalStateHandle<Record<string, unknown>> = null;

/** 由主应用在启动时注入全局状态句柄 */
export function provideGlobalState(handle: GlobalStateHandle<Record<string, unknown>>): void {
  globalStateHandle = handle;
}

/** 获取全局状态句柄（供内核适配器内部使用） */
export function getGlobalState(): null | GlobalStateHandle<Record<string, unknown>> {
  return globalStateHandle;
}

/**
 * 响应式全局状态组合式函数
 *
 * 在组件 setup 或 effect scope 中调用时，组件卸载会自动取消订阅；
 * 在 scope 外调用（极少场景）则需调用方自行管理返回的 ref 生命周期。
 *
 * @example
 * const theme = useGlobalState<'theme', 'dark' | 'light' | 'auto'>('theme');
 * theme.value;  // 'light' | 'dark' | 'auto'
 */
export function useGlobalState<K extends string, V = unknown>(key: K): Ref<null | V> {
  const value = ref<null | V>(null);

  if (!globalStateHandle) {
    console.warn('[MicroRuntime] useGlobalState: globalState not provided yet');
    return value as Ref<null | V>;
  }

  // 同步初始值
  const state = globalStateHandle.get();
  value.value = (state as Record<string, unknown>)[key] as null | V;

  // 订阅变化（subscribe 返回 unsubscribe）
  const unsubscribe = globalStateHandle.subscribe((next) => {
    value.value = (next as Record<string, unknown>)[key] as null | V;
  });

  // 组件/effect scope 销毁时自动取消订阅；
  // onScopeDispose 在无 active scope 时为 no-op，不会抛错
  onScopeDispose(() => {
    try {
      unsubscribe();
    } catch {
      /* 静默 */
    }
  });

  return value as Ref<null | V>;
}

/**
 * 响应式全局状态 ref（同 useGlobalState，但提供 .value 访问）
 */
export function useGlobalStateRef<T = unknown>(key: string, defaultValue: T): Ref<T> {
  const raw = useGlobalState<string, T>(key);
  return computed({
    get: () => raw.value ?? defaultValue,
    set: (val) => {
      if (globalStateHandle) {
        globalStateHandle.set({ [key]: val });
      }
    },
  });
}
