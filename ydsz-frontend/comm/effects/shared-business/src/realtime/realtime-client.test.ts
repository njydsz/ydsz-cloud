/**
 * realtime-client 单元测试
 *
 * @path comm\effects\shared-business\src\realtime\realtime-client.test.ts
 * @author ydsz-team
 * @since 1.1.0
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { RealtimeClient } from './realtime-client';

/** Mock WebSocket */
class MockWebSocket {
  static instances: MockWebSocket[] = [];
  readyState = WebSocket.CONNECTING;
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: any }) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  sent: string[] = [];

  constructor(public url: string) {
    MockWebSocket.instances.push(this);
  }

  send(data: string) {
    this.sent.push(data);
  }

  close() {
    this.readyState = WebSocket.CLOSED;
    this.onclose?.();
  }

  /** 测试辅助：模拟连接建立 */
  simulateOpen() {
    this.readyState = WebSocket.OPEN;
    this.onopen?.();
  }

  /** 测试辅助：模拟收到消息 */
  simulateMessage(data: any) {
    this.onmessage?.({ data });
  }
}

beforeEach(() => {
  MockWebSocket.instances = [];
  vi.stubGlobal('WebSocket', MockWebSocket);
  vi.useFakeTimers();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe('RealtimeClient', () => {
  it('connect 建立 WebSocket 连接', () => {
    const client = new RealtimeClient({ url: 'ws://test/ws' });
    client.connect();
    expect(MockWebSocket.instances).toHaveLength(1);
    expect(MockWebSocket.instances[0].url).toBe('ws://test/ws');
    client.close();
  });

  it('连接建立后状态为 open', () => {
    const client = new RealtimeClient({ url: 'ws://test/ws' });
    client.connect();
    MockWebSocket.instances[0].simulateOpen();
    expect(client.getStatus()).toBe('open');
    client.close();
  });

  it('subscribe 收到对应频道消息', () => {
    const client = new RealtimeClient({ url: 'ws://test/ws' });
    const handler = vi.fn();
    client.connect();
    MockWebSocket.instances[0].simulateOpen();
    client.subscribe('notification', handler);
    MockWebSocket.instances[0].simulateMessage(
      JSON.stringify({ channel: 'notification', payload: { id: 1 } }),
    );
    expect(handler).toHaveBeenCalledWith({ id: 1 }, 'notification');
    client.close();
  });

  it('subscribe 只接收订阅的频道', () => {
    const client = new RealtimeClient({ url: 'ws://test/ws' });
    const handlerA = vi.fn();
    const handlerB = vi.fn();
    client.connect();
    MockWebSocket.instances[0].simulateOpen();
    client.subscribe('a', handlerA);
    client.subscribe('b', handlerB);
    MockWebSocket.instances[0].simulateMessage(
      JSON.stringify({ channel: 'a', payload: 'x' }),
    );
    expect(handlerA).toHaveBeenCalled();
    expect(handlerB).not.toHaveBeenCalled();
    client.close();
  });

  it('unsubscribe 后不再收到消息', () => {
    const client = new RealtimeClient({ url: 'ws://test/ws' });
    const handler = vi.fn();
    client.connect();
    MockWebSocket.instances[0].simulateOpen();
    const unsubscribe = client.subscribe('n', handler);
    unsubscribe();
    MockWebSocket.instances[0].simulateMessage(
      JSON.stringify({ channel: 'n', payload: 'x' }),
    );
    expect(handler).not.toHaveBeenCalled();
    client.close();
  });

  it('断线后自动重连（指数退避）', () => {
    const client = new RealtimeClient({
      url: 'ws://test/ws',
      heartbeatInterval: 1000,
    });
    client.connect();
    MockWebSocket.instances[0].simulateOpen();
    MockWebSocket.instances[0].close(); // 模拟断线

    expect(client.getStatus()).toBe('reconnecting');
    // 首次退避约 0.7~1.3s，推进定时器
    vi.advanceTimersByTime(2000);
    expect(MockWebSocket.instances.length).toBeGreaterThanOrEqual(2);
    client.close();
  });

  it('手动 close 后不再重连', () => {
    const client = new RealtimeClient({ url: 'ws://test/ws' });
    client.connect();
    MockWebSocket.instances[0].simulateOpen();
    client.close();
    vi.advanceTimersByTime(5000);
    expect(MockWebSocket.instances).toHaveLength(1);
  });

  it('重连次数达到上限后停止', () => {
    const client = new RealtimeClient({
      url: 'ws://test/ws',
      maxReconnectAttempts: 2,
    });
    client.connect();
    // 循环断线-重连
    for (let i = 0; i < 5; i += 1) {
      const ws = MockWebSocket.instances[MockWebSocket.instances.length - 1];
      ws.simulateOpen();
      ws.close();
      vi.advanceTimersByTime(40000);
    }
    // 最多创建 1(初始) + 2(重连) = 3 个实例
    expect(MockWebSocket.instances.length).toBeLessThanOrEqual(3);
    client.close();
  });

  it('心跳定时发送', () => {
    const client = new RealtimeClient({
      url: 'ws://test/ws',
      heartbeatInterval: 1000,
    });
    client.connect();
    MockWebSocket.instances[0].simulateOpen();
    vi.advanceTimersByTime(3000);
    const sent = MockWebSocket.instances[0].sent;
    expect(sent.filter((s) => s.includes('__heartbeat__'))).toHaveLength(3);
    client.close();
  });
});
