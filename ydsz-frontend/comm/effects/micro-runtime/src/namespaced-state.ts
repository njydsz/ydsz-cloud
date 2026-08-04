/**
 * 命名空间隔离的 globalState
 *
 * 在现有 RawGlobalStateAPI 之上提供 scope 隔离：不同业务模块/子应用可使用
 * 独立命名空间，避免 key 冲突；底层广播通过带前缀 key 实现，共享同一 globalState 通道。
 *
 * 注入子应用 mountProps 的 `_globalState.useNamespace(scope)` 即返回隔离 API。
 *
 * @path comm/effects/micro-runtime/src/namespaced-state.ts
 * @author ydsz-team
 * @since 3.7.0
 */

import type { RawGlobalStateAPI } from './global-state';

/** 命名空间隔离的 globalState 接口（注入子应用使用） */
export interface NamespacedGlobalStateAPI {
  /** 获取当前命名空间的状态快照 */
  getScopeState(): Record<string, unknown>;
  /** 设置当前命名空间的状态 */
  setScopeState(patch: Record<string, unknown>): void;
  /** 订阅当前命名空间的状态变化 */
  onScopeStateChange(
    listener: (state: Record<string, unknown>, prev: Record<string, unknown>) => void,
    fireImmediately?: boolean,
  ): () => void;
}

/**
 * 创建命名空间管理器。
 *
 * @param raw - 底层 RawGlobalStateAPI（来自内核注入） * @param scope - 命名空间标识
 * @returns 命名空间隔离 API
 */
export function createNamespacedState(
  raw: RawGlobalStateAPI,
  scope: string,
): NamespacedGlobalStateAPI {
  const scopeKey = `__ns__${scope}__`;

  const getScopeState = (): Record<string, unknown> => {
    const all = raw.getGlobalState();
    return (all[scopeKey] as Record<string, unknown>) ?? {};
  };

  return {
    getScopeState,
    setScopeState(patch) {
      const prev = getScopeState();
      const next = { ...prev, ...patch };
      raw.setGlobalState({ [scopeKey]: next });
    },
    onScopeStateChange(listener, fireImmediately) {
      return raw.onGlobalStateChange((state, prevState) => {
        const scopePrev = (prevState?.[scopeKey] as Record<string, unknown>) ?? {};
        const scopeCurr = (state?.[scopeKey] as Record<string, unknown>) ?? {};
        // 仅当本命名空间字段变化时才触发回调
        if (scopePrev !== scopeCurr) {
          listener(scopeCurr, scopePrev);
        }
      }, fireImmediately);
    },
  };
}

/**
 * 为 globalState 增加命名空间能力（工厂函数）。
 *
 * 在子应用侧调用：
 * ```ts
 * const ns = createNamespacedGlobalState(mountProps._globalState, 'myFeature');
 * ns.setScopeState({ count: 1 });
 * ns.onScopeStateChange((state) => console.log(state.count));
 * ```
 *
 * @param raw - 来自 mountProps._globalState 的原始 API
 * @returns 含 useNamespace 方法的增强 API
 */
export function createNamespacedGlobalStateWrapper(raw: RawGlobalStateAPI) {
  return {
    ...raw,
    /** 获取指定命名空间的隔离 API */
    useNamespace(scope: string): NamespacedGlobalStateAPI {
      return createNamespacedState(raw, scope);
    },
  };
}
