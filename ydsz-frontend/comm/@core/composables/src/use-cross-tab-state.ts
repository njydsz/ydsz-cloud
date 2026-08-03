/**
 * Vue 组合式 API — 跨标签页状态同步
 *
 * 使用方式：
 *   const theme = useCrossTabState('ydsz-app', 'theme', 'light');
 *   theme.value = 'dark';  // 自动广播到其它标签页
 *
 * 设计要点：
 *   - 借助 BroadcastChannelManager 的单 channel 多 type 设计，多个 useCrossTabState 共享同一通道
 *   - 状态采用 ref，组件内可直接 v-model
 *   - 跨标签页消息到达时自动更新本地 ref，并触发 Vue 响应式更新
 *   - onScopeDispose 自动取消订阅，无需手动清理
 *
 * @path comm/@core/composables/src/use-cross-tab-state.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Ref } from 'vue';

import { onScopeDispose, ref } from 'vue';
import {
  BroadcastChannelManager,
  type BroadcastListener,
} from '@ydsz-core/shared/cache';

/** 已初始化的通道注册表：channelName → BroadcastChannelManager */
const channelMap = new Map<string, BroadcastChannelManager>();
/** 每个 channel 的引用计数，便于在所有 ref 销毁后释放通道 */
const channelRefCount = new Map<string, number>();

/**
 * 获取或创建共享广播通道（同 channelName 全局复用）。
 *
 * 使用引用计数：所有 useCrossTabState 释放后自动调用 close() 释放资源。
 *
 * echo=true 是必须的：同一标签页内多个 useCrossTabState 共享同一 manager 实例，
 * BroadcastChannel API 不会向发送者自身投递消息，因此需要 echo 将消息
 * 派发给本标签页内的其它监听器。跨标签页投递仍由 BroadcastChannel 原生处理。
 */
function acquireChannel<T = unknown>(channelName: string): BroadcastChannelManager<T> {
  let channel = channelMap.get(channelName) as
    | BroadcastChannelManager<T>
    | undefined;
  if (!channel) {
    channel = new BroadcastChannelManager<T>({ channelName, echo: true });
    channelMap.set(channelName, channel as BroadcastChannelManager);
  }
  channelRefCount.set(
    channelName,
    (channelRefCount.get(channelName) ?? 0) + 1,
  );
  return channel;
}

function releaseChannel(channelName: string): void {
  const count = channelRefCount.get(channelName) ?? 0;
  if (count <= 1) {
    channelRefCount.delete(channelName);
    const channel = channelMap.get(channelName);
    if (channel) {
      channel.close();
      channelMap.delete(channelName);
    }
  } else {
    channelRefCount.set(channelName, count - 1);
  }
}

/**
 * 跨标签页响应式状态。
 *
 * - 本地写入：广播到其它标签页
 * - 远端更新：监听广播并更新本地 ref
 * - 默认跳过自身回响，避免写入触发自身 watcher 死循环
 *
 * @param channelName 通道名（同源标签页共享）
 * @param stateKey 状态键（作为 BroadcastMessage.type）
 * @param initialValue 初始值（首次创建时使用）
 *
 * @example
 * const theme = useCrossTabState('ydsz', 'theme', 'light');
 * theme.value = 'dark';  // 其它标签页的 theme 自动同步为 'dark'
 */
export function useCrossTabState<T>(
  channelName: string,
  stateKey: string,
  initialValue: T,
): Ref<T> {
  const channel = acquireChannel<T>(channelName);
  const state = ref<T>(initialValue) as Ref<T>;

  // 订阅远端消息
  const listener: BroadcastListener<T> = (message) => {
    state.value = message.payload;
  };
  const unsubscribe = channel.on(stateKey, listener);

  // 组件卸载时清理
  onScopeDispose(() => {
    try {
      unsubscribe();
    } catch {
      /* 静默 */
    }
    releaseChannel(channelName);
  });

  /**
   * 通过 ref.value 赋值不会自动广播；显式提供 broadcast 方法。
   *
   * 之所以不直接劫持 setter，是因为劫持会带来：
   *   - 与 v-model 双向绑定的隐式副作用（远端更新触发本地 setter → 再次广播）
   *   - 调试困难（无法区分本地修改与远端同步）
   *
   * 显式 broadcast 让"本地写入 → 广播"这一动作可被追踪。
   */
  (state as Ref<T> & { broadcast?: (value: T) => void }).broadcast = (
    value: T,
  ) => {
    state.value = value;
    channel.postMessage(stateKey, value);
  };

  return state;
}

/**
 * 跨标签页事件订阅（仅监听，不持有本地状态）。
 *
 * 适用于"登出"、"刷新通知"等一次性事件场景。
 *
 * @returns 取消订阅函数（onScopeDispose 自动调用）
 */
export function useCrossTabEvent<T = unknown>(
  channelName: string,
  eventType: string,
  handler: (payload: T) => void,
): () => void {
  const channel = acquireChannel<T>(channelName);
  const listener: BroadcastListener<T> = (message) => {
    handler(message.payload);
  };
  const unsubscribe = channel.on(eventType, listener);

  onScopeDispose(() => {
    try {
      unsubscribe();
    } catch {
      /* 静默 */
    }
    releaseChannel(channelName);
  });

  return unsubscribe;
}

/**
 * 向所有标签页广播一次性事件。
 *
 * 用于"登出"、"会话失效"、"全员刷新"等场景。
 */
export function broadcastCrossTabEvent<T = unknown>(
  channelName: string,
  eventType: string,
  payload: T,
): void {
  const channel = acquireChannel<T>(channelName);
  channel.postMessage(eventType, payload);
  // 立即释放本次广播的引用计数（事件不持有状态）
  releaseChannel(channelName);
}
