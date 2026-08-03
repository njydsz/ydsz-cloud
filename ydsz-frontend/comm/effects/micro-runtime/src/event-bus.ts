/**
 * 微应用事件总线 — 一次性消息传递
 *
 * 场景：消息已读刷新角标、租户切换通知所有子应用刷新数据。
 * 事件名集中在 MicroEvents 常量注册表，禁止散写字符串 key。
 *
 * @path comm/effects/micro-runtime/src/event-bus.ts
 * @author ydsz-team
 * @since 3.0.0
 */

/** 微应用事件总线的事件回调签名，接收事件负载 */
export type EventHandler<T = unknown> = (payload: T) => void;

/** 微应用事件总线接口：支持一次性消息的发布、订阅与退订 */
export interface MicroEventBus {
  emit<T = unknown>(event: string, payload: T): void;
  on<T = unknown>(event: string, handler: EventHandler<T>): () => void;
  off<T = unknown>(event: string, handler: EventHandler<T>): void;
}

/** 创建一个微应用事件总线实例，事件名需集中在常量注册表中使用 */
export function createEventBus(): MicroEventBus {
  const listeners = new Map<string, Set<EventHandler>>();

  return {
    emit(event, payload) {
      for (const handler of listeners.get(event) || []) {
        try {
          handler(payload);
        } catch (error) {
          console.error(`[MicroEventBus] Handler error on "${event}":`, error);
        }
      }
    },
    on(event, handler) {
      if (!listeners.has(event)) {
        listeners.set(event, new Set());
      }
      listeners.get(event)!.add(handler);
      return () => {
        listeners.get(event)?.delete(handler);
      };
    },
    off(event, handler) {
      listeners.get(event)?.delete(handler);
      if (listeners.get(event)?.size === 0) {
        listeners.delete(event);
      }
    },
  };
}
