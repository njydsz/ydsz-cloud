/**
 * use-realtime 组合式函数 — 实时消息订阅
 *
 * @path comm\effects\shared-business\src\realtime\use-realtime.ts
 * @author ydsz-team
 * @since 1.1.0
 *
 * @remarks
 * 组件内订阅实时频道，卸载自动取消订阅。
 * 单例连接管理：同一 url 只建立一个 WebSocket，多组件共享。
 */
import { onBeforeUnmount } from 'vue';

import { RealtimeClient, type RealtimeOptions } from './realtime-client';

/** url → 客户端实例（模块级单例） */
const clientMap = new Map<string, RealtimeClient>();

function getClient(options: RealtimeOptions): RealtimeClient {
  let client = clientMap.get(options.url);
  if (!client) {
    client = new RealtimeClient(options);
    clientMap.set(options.url, client);
  }
  return client;
}

/**
 * 订阅实时频道
 *
 * @param options - 连接配置（首次调用建立连接）
 * @param channel - 频道名
 * @param handler - 消息回调
 * @param connectImmediately - 是否立即连接，默认 true
 *
 * @example
 * ```ts
 * const { send } = useRealtime(
 *   { url: 'wss://host/ws' },
 *   'notification',
 *   (payload) => notificationStore.push(payload),
 * );
 * ```
 */
export function useRealtime(
  options: RealtimeOptions,
  channel: string,
  handler: (payload: any, channel: string) => void,
  connectImmediately = true,
) {
  const client = getClient(options);
  if (connectImmediately) {
    client.connect();
  }

  const unsubscribe = client.subscribe(channel, handler);

  onBeforeUnmount(unsubscribe);

  return {
    /** 发送消息到服务端 */
    send: (data: any) => client.send(data),
    /** 获取连接状态 */
    status: () => client.getStatus(),
    /** 关闭连接（影响所有共享该连接的组件） */
    close: () => client.close(),
  };
}

/** 获取全局唯一的 RealtimeClient 实例（非组件场景使用） */
export function getRealtimeClient(options: RealtimeOptions): RealtimeClient {
  return getClient(options);
}
