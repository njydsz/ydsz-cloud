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

export type EventHandler<T = unknown> = (payload: T) => void;

export interface MicroEventBus {
  emit<T = unknown>(event: string, payload: T): void;
  on<T = unknown>(event: string, handler: EventHandler<T>): () => void;
  off<T = unknown>(event: string, handler: EventHandler<T>): void;
}

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
