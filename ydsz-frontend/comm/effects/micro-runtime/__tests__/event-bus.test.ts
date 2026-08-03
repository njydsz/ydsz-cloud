/**
 * event-bus 模块单元测试
 *
 * @path comm/effects/micro-runtime/__tests__/event-bus.test.ts
 * @author ydsz-team
 * @since 3.0.0
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createEventBus } from '../src/event-bus';

describe('event-bus', () => {
  let bus: ReturnType<typeof createEventBus>;

  beforeEach(() => {
    bus = createEventBus();
  });

  it('应能 emit 并接收事件', () => {
    const handler = vi.fn();
    bus.on('test', handler);
    bus.emit('test', { value: 42 });
    expect(handler).toHaveBeenCalledWith({ value: 42 });
  });

  it('应能取消订阅', () => {
    const handler = vi.fn();
    const unsub = bus.on('test', handler);
    unsub();
    bus.emit('test', {});
    expect(handler).not.toHaveBeenCalled();
  });

  it('应能 off 指定 handler', () => {
    const handler1 = vi.fn();
    const handler2 = vi.fn();
    bus.on('test', handler1);
    bus.on('test', handler2);
    bus.off('test', handler1);
    bus.emit('test', {});
    expect(handler1).not.toHaveBeenCalled();
    expect(handler2).toHaveBeenCalled();
  });

  it('handler 抛错不应阻断其他 handler', () => {
    const badHandler = vi.fn(() => { throw new Error('oops'); });
    const goodHandler = vi.fn();
    bus.on('test', badHandler);
    bus.on('test', goodHandler);
    bus.emit('test', {});
    expect(goodHandler).toHaveBeenCalled();
  });

  it('未订阅的事件 emit 不应报错', () => {
    expect(() => bus.emit('no-such-event', {})).not.toThrow();
  });
});
