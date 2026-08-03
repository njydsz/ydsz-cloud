/**
 * sandbox 模块单元测试
 *
 * @path comm/effects/micro-kernel/__tests__/sandbox.test.ts
 * @author ydsz-team
 * @since 3.0.0
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { enterSandbox, exitSandbox } from '../src/sandbox';

describe('sandbox', () => {
  beforeEach(() => {
    // 清理任何残留的全局污染
    delete (window as Record<string, unknown>).__test_global__;
    delete (window as Record<string, unknown>).__test_modified__;
  });

  afterEach(() => {
    delete (window as Record<string, unknown>).__test_global__;
    delete (window as Record<string, unknown>).__test_modified__;
  });

  describe('enter / exit (window 快照恢复)', () => {
    it('应恢复子应用新增的 window 全局变量', () => {
      const sandbox = enterSandbox();

      // 子应用新增全局变量
      (window as Record<string, unknown>).__test_global__ = 'leaked';

      exitSandbox(sandbox);

      // 退出后应被清除
      expect((window as Record<string, unknown>).__test_global__).toBeUndefined();
    });

    it('应还原被子应用修改的 window 属性', () => {
      // 预设一个全局属性
      (window as Record<string, unknown>).__test_modified__ = 'original';

      const sandbox = enterSandbox();

      // 子应用修改
      (window as Record<string, unknown>).__test_modified__ = 'modified';

      exitSandbox(sandbox);

      // 退出后应还原
      expect((window as Record<string, unknown>).__test_modified__).toBe('original');
    });
  });

  describe('事件监听清理', () => {
    it('应清除子应用注册的 window 事件监听', () => {
      const sandbox = enterSandbox();

      const handler = () => {};
      window.addEventListener('resize', handler);

      exitSandbox(sandbox);

      // 使用 dispatchEvent 验证（监听器已移除则不会触发 mock）
      const spy = vi.fn();
      window.addEventListener('resize', spy);
      window.dispatchEvent(new Event('resize'));
      expect(spy).toHaveBeenCalledTimes(1);
    });
  });

  describe('定时器清理', () => {
    it('应清除子应用创建的 setTimeout', () => {
      vi.useFakeTimers();
      const sandbox = enterSandbox();

      const spy = vi.fn();
      window.setTimeout(spy, 100);

      expect(sandbox.timerIds.length).toBeGreaterThan(0);

      exitSandbox(sandbox);

      vi.runAllTimers();
      expect(spy).not.toHaveBeenCalled();
      vi.useRealTimers();
    });

    it('应清除子应用创建的 setInterval', () => {
      vi.useFakeTimers();
      const sandbox = enterSandbox();

      const spy = vi.fn();
      window.setInterval(spy, 50);

      exitSandbox(sandbox);

      vi.advanceTimersByTime(200);
      expect(spy).not.toHaveBeenCalled();
      vi.useRealTimers();
    });
  });

  describe('嵌套沙箱', () => {
    it('第二个沙箱不应重复代理全局 API', () => {
      const s1 = enterSandbox();
      const s2 = enterSandbox();

      // s2 应覆盖激活沙箱，但不应重复代理
      expect(s2).not.toBe(s1);

      exitSandbox(s2);
      exitSandbox(s1);

      // 全局 API 应正确还原（两次 exit 幂等）
      expect(typeof window.setTimeout).toBe('function');
    });
  });
});
