/**
 * useCrossTabState 测试
 *
 * 覆盖：
 *   - ref 初始值
 *   - broadcast 触发远端接收
 *   - 远端消息更新本地 ref
 *   - onScopeDispose 清理订阅
 *
 * @path comm/@core/composables/src/__tests__/use-cross-tab-state.test.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { effectScope, ref } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { broadcastCrossTabEvent, useCrossTabEvent, useCrossTabState } from '../use-cross-tab-state';

type ChannelListener = (event: { data: unknown }) => void;

class MockBroadcastChannel {
  static instances: MockBroadcastChannel[] = [];
  static readonly: Record<string, ChannelListener[]> = {};

  readonly name: string;
  private listeners: ChannelListener[] = [];

  constructor(name: string) {
    this.name = name;
    MockBroadcastChannel.instances.push(this);
  }

  addEventListener(_type: string, listener: ChannelListener): void {
    this.listeners.push(listener);
    if (!MockBroadcastChannel.readonly[this.name]) {
      MockBroadcastChannel.readonly[this.name] = [];
    }
    MockBroadcastChannel.readonly[this.name].push(listener);
  }

  removeEventListener(_type: string, listener: ChannelListener): void {
    this.listeners = this.listeners.filter((l) => l !== listener);
    MockBroadcastChannel.readonly[this.name] = (
      MockBroadcastChannel.readonly[this.name] || []
    ).filter((l) => l !== listener);
  }

  postMessage(data: unknown): void {
    for (const instance of MockBroadcastChannel.instances) {
      if (instance === this) continue;
      for (const listener of [...instance.listeners]) {
        listener({ data });
      }
    }
  }

  close(): void {
    this.listeners = [];
    MockBroadcastChannel.instances = MockBroadcastChannel.instances.filter(
      (i) => i !== this,
    );
  }
}

describe('use-cross-tab-state', () => {
  beforeEach(() => {
    (globalThis as { BroadcastChannel?: typeof MockBroadcastChannel })
      .BroadcastChannel = MockBroadcastChannel;
    MockBroadcastChannel.instances = [];
    MockBroadcastChannel.readonly = {};
    localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    (globalThis as { BroadcastChannel?: undefined }).BroadcastChannel = undefined;
  });

  describe('useCrossTabState', () => {
    it('返回的 ref 具有初始值', () => {
      const scope = effectScope();
      scope.run(() => {
        const theme = useCrossTabState('test-scope-1', 'theme', 'light');
        expect(theme.value).toBe('light');
      });
      scope.stop();
    });

    it('broadcast 应将新值广播到其它标签页', () => {
      const scope1 = effectScope();
      const scope2 = effectScope();

      // 模拟两个标签页：每个 scope 各自 useCrossTabState
      const theme1 = scope1.run(() =>
        useCrossTabState<string>('test-scope-2', 'theme', 'light'),
      )!;
      const theme2 = scope2.run(() =>
        useCrossTabState<string>('test-scope-2', 'theme', 'light'),
      )!;

      // 标签页1广播新值
      (theme1 as ref<string> & { broadcast?: (v: string) => void }).broadcast?.('dark');

      // 标签页2应收到
      expect(theme2.value).toBe('dark');

      scope1.stop();
      scope2.stop();
    });

    it('直接 ref.value 赋值不广播（仅本地修改）', () => {
      const scope1 = effectScope();
      const scope2 = effectScope();

      const theme1 = scope1.run(() =>
        useCrossTabState<string>('test-scope-3', 'theme', 'light'),
      )!;
      const theme2 = scope2.run(() =>
        useCrossTabState<string>('test-scope-3', 'theme', 'light'),
      )!;

      // 直接赋值，不应广播
      theme1.value = 'dark';
      expect(theme2.value).toBe('light');

      scope1.stop();
      scope2.stop();
    });

    it('scope 销毁时自动清理订阅', () => {
      const scope1 = effectScope();
      const scope2 = effectScope();

      const theme1 = scope1.run(() =>
        useCrossTabState<string>('test-scope-4', 'theme', 'light'),
      )!;
      const theme2 = scope2.run(() =>
        useCrossTabState<string>('test-scope-4', 'theme', 'light'),
      )!;

      // 停止 scope1 → theme1 的监听器应被清理
      scope1.stop();

      // theme2 广播 'dark' → theme2 收到（自身 echo），theme1 不应收到（已退订）
      (theme2 as ref<string> & { broadcast?: (v: string) => void }).broadcast?.('dark');
      expect(theme2.value).toBe('dark');
      expect(theme1.value).toBe('light');

      scope2.stop();
    });
  });

  describe('useCrossTabEvent', () => {
    it('监听一次性事件', () => {
      const scope1 = effectScope();
      const scope2 = effectScope();

      scope1.run(() => {
        const handler = vi.fn();
        useCrossTabEvent('event-test', 'logout', handler);

        // 标签页2广播 logout 事件
        scope2.run(() => {
          broadcastCrossTabEvent('event-test', 'logout', { reason: 'expired' });
        });

        expect(handler).toHaveBeenCalledTimes(1);
        expect(handler).toHaveBeenCalledWith({ reason: 'expired' });
      });

      scope1.stop();
      scope2.stop();
    });
  });
});
