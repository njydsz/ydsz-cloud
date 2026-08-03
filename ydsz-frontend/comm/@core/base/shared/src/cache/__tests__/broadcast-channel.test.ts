/**
 * BroadcastChannelManager 测试
 *
 * 覆盖：
 *   - 基础发布订阅
 *   - 跨实例去重（origin 跳过自身）
 *   - 版本兼容性检查
 *   - localStorage 兜底（通过 polyfill 注入）
 *   - 共享通道注册表
 *
 * @path comm/@core/base/shared/src/cache/__tests__/broadcast-channel.test.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  BroadcastChannelManager,
  getSharedBroadcastChannel,
  isBroadcastChannelSupported,
  releaseSharedBroadcastChannel,
} from '../broadcast-channel';

// Mock BroadcastChannel API（happy-dom 不原生支持）
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
    // 模拟跨实例广播：同一 channel 名下的其它实例收到消息
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

describe('broadcast-channel', () => {
  beforeEach(() => {
    // 注入 MockBroadcastChannel
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

  describe('BroadcastChannelManager - 基础订阅', () => {
    it('on/once 订阅应能收到 postMessage', () => {
      const sender = new BroadcastChannelManager({ channelName: 'test-1' });
      const receiver = new BroadcastChannelManager({ channelName: 'test-1' });

      const received = vi.fn();
      const receivedOnce = vi.fn();
      receiver.on('hello', received);
      receiver.once('hello-once', receivedOnce);

      sender.postMessage('hello', { msg: 'world' });
      sender.postMessage('hello-once', { value: 1 });
      // 第二次 once 不应触发
      sender.postMessage('hello-once', { value: 2 });

      expect(received).toHaveBeenCalledTimes(1);
      expect(received).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 'hello',
          payload: { msg: 'world' },
        }),
      );
      expect(receivedOnce).toHaveBeenCalledTimes(1);
      expect(receivedOnce).toHaveBeenCalledWith(
        expect.objectContaining({ payload: { value: 1 } }),
      );

      sender.close();
      receiver.close();
    });

    it('on 返回的 unsubscribe 应能取消订阅', () => {
      const sender = new BroadcastChannelManager({ channelName: 'test-2' });
      const receiver = new BroadcastChannelManager({ channelName: 'test-2' });

      const listener = vi.fn();
      const off = receiver.on('event', listener);
      off();

      sender.postMessage('event', null);
      expect(listener).not.toHaveBeenCalled();

      sender.close();
      receiver.close();
    });

    it('close 应清理所有监听器', () => {
      const sender = new BroadcastChannelManager({ channelName: 'test-3' });
      const receiver = new BroadcastChannelManager({ channelName: 'test-3' });

      const listener = vi.fn();
      receiver.on('event', listener);
      receiver.close();

      sender.postMessage('event', null);
      expect(listener).not.toHaveBeenCalled();

      sender.close();
    });
  });

  describe('跨实例去重（origin）', () => {
    it('默认 echo=false 时接收端跳过自身发送的消息', () => {
      const instance = new BroadcastChannelManager({
        channelName: 'echo-test',
        instanceId: 'me',
      });

      const listener = vi.fn();
      instance.on('ping', listener);

      // postMessage 默认不触发本实例监听器
      instance.postMessage('ping', { v: 1 });
      expect(listener).not.toHaveBeenCalled();

      instance.close();
    });

    it('echo=true 时接收端可收到自身发送的消息', () => {
      const instance = new BroadcastChannelManager({
        channelName: 'echo-test-2',
        echo: true,
      });

      const listener = vi.fn();
      instance.on('ping', listener);

      instance.postMessage('ping', { v: 1 });
      expect(listener).toHaveBeenCalledTimes(1);

      instance.close();
    });

    it('postMessage options.echo 覆盖构造期 echo 设置', () => {
      const instance = new BroadcastChannelManager({
        channelName: 'echo-test-3',
        echo: false,
      });

      const listener = vi.fn();
      instance.on('ping', listener);

      // 默认不回响
      instance.postMessage('ping', 'a');
      expect(listener).not.toHaveBeenCalled();

      // 显式 echo=true 触发本实例监听器
      instance.postMessage('ping', 'b', { echo: true });
      expect(listener).toHaveBeenCalledTimes(1);

      instance.close();
    });
  });

  describe('版本兼容性', () => {
    it('版本不匹配的消息应被丢弃并告警', () => {
      const sender = new BroadcastChannelManager({
        channelName: 'ver-test',
        version: 1,
      });
      const receiver = new BroadcastChannelManager({
        channelName: 'ver-test',
        version: 2,
      });

      const listener = vi.fn();
      const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
      receiver.on('event', listener);

      sender.postMessage('event', { v: 1 });

      expect(listener).not.toHaveBeenCalled();
      expect(warn).toHaveBeenCalledWith(
        expect.stringContaining('version mismatch'),
      );

      sender.close();
      receiver.close();
    });

    it('未指定 version 的消息（v=undefined）应被接受', () => {
      const sender = new BroadcastChannelManager({
        channelName: 'ver-test-2',
        version: 5,
      });
      const receiver = new BroadcastChannelManager({
        channelName: 'ver-test-2',
        version: 5,
      });

      // 模拟外部发送方（无 v 字段）
      const externalChannel = new MockBroadcastChannel('ver-test-2');
      for (const listener of MockBroadcastChannel.readonly['ver-test-2'] ||
        []) {
        // receiver 已通过 BroadcastChannelManager 构造订阅
      }
      // 直接调用 receiver 的 handleMessage
      const listener = vi.fn();
      receiver.on('external-event', listener);

      // 通过 mock channel 直接派发一条无 v 字段的消息
      const externalListener = MockBroadcastChannel.readonly['ver-test-2'];
      for (const l of externalListener || []) {
        l({
          data: {
            type: 'external-event',
            payload: 'no-version',
            origin: 'external',
          },
        });
      }

      expect(listener).toHaveBeenCalledTimes(1);

      sender.close();
      receiver.close();
    });
  });

  describe('共享通道注册表', () => {
    it('同名 channel 多次获取返回同一实例', () => {
      const a = getSharedBroadcastChannel('shared-1');
      const b = getSharedBroadcastChannel('shared-1');
      expect(a).toBe(b);
      releaseSharedBroadcastChannel('shared-1');
    });

    it('releaseSharedBroadcastChannel 关闭实例后下次获取为新实例', () => {
      const a = getSharedBroadcastChannel('shared-2');
      releaseSharedBroadcastChannel('shared-2');
      const b = getSharedBroadcastChannel('shared-2');
      expect(a).not.toBe(b);
      releaseSharedBroadcastChannel('shared-2');
    });
  });

  describe('localStorage 兜底', () => {
    it('BroadcastChannel 不可用时走 storage 事件', () => {
      // 临时移除 BroadcastChannel
      const original =
        (globalThis as { BroadcastChannel?: typeof MockBroadcastChannel })
          .BroadcastChannel;
      (globalThis as { BroadcastChannel?: undefined }).BroadcastChannel =
        undefined;

      try {
        const sender = new BroadcastChannelManager({
          channelName: 'fallback-test',
          instanceId: 'sender',
        });
        const receiver = new BroadcastChannelManager({
          channelName: 'fallback-test',
          instanceId: 'receiver',
        });

        const listener = vi.fn();
        receiver.on('event', listener);

        // 直接写入 localStorage（模拟跨标签页 storage 事件）
        const message = {
          type: 'event',
          payload: { hello: 'world' },
          origin: 'sender',
          v: 1,
        };
        localStorage.setItem(
          'fallback-test:broadcast',
          JSON.stringify(message),
        );

        // 手动触发 storage 事件（happy-dom 不会自动触发）
        const storageEvent = new StorageEvent('storage', {
          key: 'fallback-test:broadcast',
          newValue: JSON.stringify(message),
        });
        window.dispatchEvent(storageEvent);

        expect(listener).toHaveBeenCalledTimes(1);

        sender.close();
        receiver.close();
      } finally {
        (globalThis as { BroadcastChannel?: typeof MockBroadcastChannel })
          .BroadcastChannel = original;
      }
    });

    it('isBroadcastChannelSupported 反映当前环境能力', () => {
      // 注入了 MockBroadcastChannel，应返回 true
      expect(isBroadcastChannelSupported()).toBe(true);

      // 移除后应返回 false
      const original =
        (globalThis as { BroadcastChannel?: typeof MockBroadcastChannel })
          .BroadcastChannel;
      (globalThis as { BroadcastChannel?: undefined }).BroadcastChannel =
        undefined;
      expect(isBroadcastChannelSupported()).toBe(false);
      (globalThis as { BroadcastChannel?: typeof MockBroadcastChannel })
        .BroadcastChannel = original;
    });
  });

  describe('JSON 序列化失败', () => {
    it('payload 含循环引用时应告警且不抛错', () => {
      const sender = new BroadcastChannelManager({
        channelName: 'circular',
      });
      const err = vi.spyOn(console, 'error').mockImplementation(() => {});

      const circular: Record<string, unknown> = {};
      circular.self = circular;
      // 不应抛错
      sender.postMessage('event', circular);
      expect(err).toHaveBeenCalledWith(
        expect.stringContaining('Failed to serialize'),
        expect.anything(),
      );

      sender.close();
    });
  });
});
