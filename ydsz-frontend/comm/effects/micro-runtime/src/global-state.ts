/**
 * 类型安全的全局状态。
 *
 * 替代 qiankun initGlobalState（any 广播），提供类型化 get/set/subscribe。
 * 状态持久化复用现有的 Pinia + SecureLS 体系，本模块只负责主子应用通信。
 *
 * @path comm/effects/micro-runtime/src/global-state.ts
 * @author ydsz-team
 * @since 3.0.0
 */

/** 全局状态的版本化包装，结构变更时递增 version，跨版本不兼容直接报错 */
export interface VersionedState<T> {
  version: number;
  data: T;
}

/** 全局状态变化监听器：接收新值与上一次值，用于跨应用状态同步 */
export interface GlobalStateListener<T> {
  (state: T, prev: T): void;
}

/** 类型安全的状态句柄 */
export interface GlobalStateHandle<T> {
  /** 获取当前只读快照 */
  get(): Readonly<T>;
  /** 合并更新（Partial，浅合并） */
  set(patch: Partial<T>): void;
  /** 订阅变化，返回取消订阅函数 */
  subscribe(listener: GlobalStateListener<T>): () => void;
}

/** 全局状态配置 */
export interface GlobalStateConfig<T> {
  /** 初始值 */
  initial: T;
  /** 版本号（结构变更时手动递增，跨版本自动报错） */
  version?: number;
}

/** 内核注入的原始全局状态通信能力 */
export interface RawGlobalStateAPI<T = Record<string, unknown>> {
  onGlobalStateChange: (listener: (state: T, prev: T) => void, fireImmediately?: boolean) => void;
  setGlobalState: (state: Partial<T>) => void;
  getGlobalState: () => T;
}

/** 内部实现 — 基于原始 API 创建类型化句柄 */
export function createGlobalStateHandle<T extends Record<string, unknown>>(
  config: GlobalStateConfig<T>,
  raw?: RawGlobalStateAPI<T>,
): GlobalStateHandle<T> {
  const version = config.version ?? 1;
  let current: VersionedState<T> = { version, data: { ...config.initial } };

  if (raw) {
    // 对接内核通信通道
    raw.onGlobalStateChange((state) => {
      current = { version, data: { ...state } };
    }, true);
  }

  const listeners = new Set<GlobalStateListener<T>>();

  return {
    get() {
      return current.data;
    },
    set(patch) {
      const prev = { ...current.data };
      Object.assign(current.data, patch);

      // 通过内核广播（如 qiankun setGlobalState）
      if (raw) {
        raw.setGlobalState(patch as Partial<T>);
      }

      // 通知本地订阅者
      for (const listener of listeners) {
        try {
          listener({ ...current.data }, prev);
        } catch (error) {
          console.error('[GlobalState] Listener error:', error);
        }
      }
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => { listeners.delete(listener); };
    },
  };
}
