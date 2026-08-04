/**
 * 子应用侧标准化 Props 访问器（v4.0 P1-2）
 *
 * 为子应用提供类型化的 mountProps 访问能力，消除手工解构的样板代码。
 *
 * 核心能力：
 * - useMicroProps<T>() - 在 setup 上下文中获取完整标准化 props
 * - useGlobalState() - 快捷访问全局状态
 * - useMessageBus() - 快捷访问消息总线
 * - useSubAppContext() - 快捷访问运行时上下文
 * - useNamespace(scope) - 获取命名空间隔离的状态 API
 *
 * 使用方式：
 * ```ts
 * // 在子应用 setup 中
 * const props = useMicroProps();
 * props.globalState.setGlobalState({ theme: 'dark' });
 * props.messageBus.sendMessage('refresh-list');
 *
 * // 命名空间隔离
 * const ns = useNamespace('my-feature');
 * ns.setScopeState({ count: 1 });
 * ```
 *
 * @path comm/effects/micro-runtime/src/use-micro-props.ts
 * @author ydsz-team
 * @since 4.0.0
 */

import type { InjectionKey } from 'vue';

import {
  getCurrentInstance,
  inject,
} from 'vue';

import type {
  EnhancedGlobalStateAPI,
  MessageBusAPI,
  StandardMicroProps,
  SubAppContext,
} from './standard-props';
import type { NamespacedGlobalStateAPI } from './namespaced-state';

// ==================== 注入 key ====================

/** 子应用 mountProps 注入 key（Symbol 避免冲突） */
export const MICRO_PROPS_KEY: InjectionKey<StandardMicroProps> = Symbol('__MICRO_PROPS__');

// ==================== 默认实现（独立运行模式兜底） ====================

/**
 * 创建默认的 props 实现（独立运行 / 非微前端环境兜底）
 *
 * 子应用独立运行时（如开发调试、SSR、单元测试），没有主应用注入 props，
 * 此默认实现提供合理的降级能力。
 */
function createDefaultProps(): StandardMicroProps {
  // 内存中的简单 globalState 模拟器
  let _state: Record<string, unknown> = {};
  const _listeners = new Set<(state: Record<string, unknown>, prev: Record<string, unknown>) => void>();

  const rawGlobalState: EnhancedGlobalStateAPI['onGlobalStateChange'] extends never
    ? never
    : {
        onGlobalStateChange: (listener: (state: Record<string, unknown>, prev: Record<string, unknown>) => void, fireImmediately?: boolean) => () => void;
        setGlobalState: (patch: Record<string, unknown>) => void;
        getGlobalState: () => Record<string, unknown>;
        useNamespace: (scope: string) => NamespacedGlobalStateAPI;
      } = {
    onGlobalStateChange(listener, fireImmediately) {
      _listeners.add(listener);
      if (fireImmediately) {
        try {
          listener({ ..._state }, {});
        } catch {
          /* 静默 */
        }
      }
      return () => {
        _listeners.delete(listener);
      };
    },
    setGlobalState(patch) {
      const prev = { ..._state };
      Object.assign(_state, patch);
      const snapshot = { ..._state };
      for (const listener of _listeners) {
        try {
          listener(snapshot, prev);
        } catch {
          /* 静默 */
        }
      }
    },
    getGlobalState() {
      return { ..._state };
    },
    useNamespace(scope: string): NamespacedGlobalStateAPI {
      const nsState = `ns:${scope}`;
      return {
        getScopeState: () => (_state[nsState] as Record<string, unknown>) ?? {},
        setScopeState(patch) {
          const prev = (_state[nsState] as Record<string, unknown>) ?? {};
          _state[nsState] = { ...prev, ...patch };
        },
        onScopeStateChange(listener, fireImmediately) {
          return rawGlobalState.onGlobalStateChange(
            (state, prevState) => {
              const scopePrev = (prevState?.[nsState] as Record<string, unknown>) ?? {};
              const scopeCurr = (state?.[nsState] as Record<string, unknown>) ?? {};
              if (scopePrev !== scopeCurr) {
                listener(scopeCurr, scopePrev);
              }
            },
            fireImmediately,
          );
        },
      };
    },
  };

  return {
    appName: (import.meta.env.VITE_APP_NAME as string) || 'standalone',
    basename: (import.meta.env.BASE_URL as string) || '/',
    container: (document.getElementById('app') as HTMLElement) || document.createElement('div'),
    sandbox: 'snapshot',
    globalState: rawGlobalState as EnhancedGlobalStateAPI,
    messageBus: {
      sendMessage: (_action: string, _payload?: unknown) => `standalone-${Date.now()}`,
      sendRequest: async <R = unknown>(
        action: string,
        _payload?: unknown,
        _timeout?: number,
      ): Promise<R> => {
        console.warn(`[Standalone] sendRequest("${action}") called but no parent kernel available`);
        return undefined as R;
      },
      registerHandler: <_T = unknown, _R = unknown>(
        _handler: never,
      ) => {
        return () => {};
      },
    },
    context: {
      appName: (import.meta.env.VITE_APP_NAME as string) || 'standalone',
      basename: (import.meta.env.BASE_URL as string) || '/',
      sandbox: 'snapshot',
    },
  };
}

// ==================== 核心 composable ====================

/**
 * 获取标准化 mount props
 *
 * 在子应用 setup 函数中使用，返回完整的标准化 props 对象。
 * 在微前端模式下从注入获取；独立运行模式下返回默认实现。
 *
 * @returns 标准化 mount props
 */
export function useMicroProps(): StandardMicroProps {
  const injected = inject<StandardMicroProps | null>(MICRO_PROPS_KEY, null);
  if (injected) {
    return injected;
  }
  return createDefaultProps();
}

/**
 * 获取增强型全局状态 API
 *
 * @example
 * const gs = useGlobalState();
 * gs.setGlobalState({ theme: 'dark' });
 */
export function useGlobalState(): EnhancedGlobalStateAPI {
  return useMicroProps().globalState;
}

/**
 * 获取点对点消息总线 API
 *
 * @example
 * const bus = useMessageBus();
 * bus.sendMessage('refresh-data');
 * const result = await bus.sendRequest('fetch-user', { id: 123 });
 */
export function useMessageBus(): MessageBusAPI {
  return useMicroProps().messageBus;
}

/**
 * 获取子应用运行时上下文
 *
 * @example
 * const ctx = useSubAppContext();
 * console.log(ctx.appName, ctx.sandbox);
 */
export function useSubAppContext(): SubAppContext {
  return useMicroProps().context;
}

/**
 * 获取指定命名空间的隔离状态 API
 *
 * @param scope - 命名空间标识
 * @returns 命名空间隔离的状态 API
 *
 * @example
 * const ns = useNamespace('project-list');
 * ns.setScopeState({ filters: { status: 'active' } });
 * ns.onScopeStateChange((state) => {
 *   console.log('Filters changed:', state.filters);
 * });
 */
export function useNamespace(scope: string): NamespacedGlobalStateAPI {
  return useMicroProps().globalState.useNamespace(scope);
}

/**
 * 向父应用注册 mount props（子应用侧调用）
 *
 * 在 defineSubApp 的 mount 函数中调用，将 props 注入组件树，
 * 使子组件能通过 useMicroProps() 访问标准化 props。
 */
export function provideMicroProps(props: StandardMicroProps): void {
  try {
    // 在 Vue 组件上下文中调用 getCurrentInstance 是安全的；
    // 在独立运行 / SSR 等非组件上下文，静默跳过（props 参数仍可通过函数传递）
    const instance = getCurrentInstance();
    if (instance) {
      const { appContext } = instance;
      appContext.app.provide(MICRO_PROPS_KEY, props);
    }
  } catch {
    // SSR / 非组件上下文：静默跳过
  }
}
