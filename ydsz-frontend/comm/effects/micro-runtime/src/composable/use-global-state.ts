/**
 * Vue 组合式 API — 子应用侧使用全局状态
 *
 * 子应用无需关心底层是 qiankun 还是 micro-kernel，
 * 始终通过 useGlobalState 获取类型化的响应式状态。
 *
 * @path comm/effects/micro-runtime/src/composable/use-global-state.ts
 * @author ydsz-team
 * @since 3.0.0
 */

import type { Ref } from 'vue';
import { computed, ref, watch } from 'vue';
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
 * @example
 * const theme = useGlobalState('theme');
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

  // 订阅变化
  const unsubscribe = globalStateHandle.subscribe((next) => {
    value.value = (next as Record<string, unknown>)[key] as null | V;
  });

  // cleanup 当组件卸载
  if (typeof window !== 'undefined') {
    // 延迟清理：Vue 组件卸载时自动取消订阅
    watch(value, () => {}, { flush: 'sync' });
  }

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
