/**
 * error-boundary 模块单元测试
 *
 * @path comm/effects/micro-kernel-lite/__tests__/error-boundary.test.ts
 * @author ydsz-team
 * @since 3.0.0
 */
import { beforeEach, describe, expect, it } from 'vitest';
import {
  clearDegraded,
  isDegraded,
  markDegraded,
  renderErrorFallback,
} from '../src/error-boundary';

describe('error-boundary', () => {
  beforeEach(() => {
    clearDegraded();
    document.body.innerHTML = '';
  });

  describe('markDegraded / isDegraded / clearDegraded', () => {
    it('应正确标记降级', () => {
      expect(isDegraded('apple')).toBe(false);
      markDegraded('apple');
      expect(isDegraded('apple')).toBe(true);
    });

    it('clearDegraded 应清空所有标记', () => {
      markDegraded('a');
      markDegraded('b');
      clearDegraded();
      expect(isDegraded('a')).toBe(false);
      expect(isDegraded('b')).toBe(false);
    });
  });

  describe('renderErrorFallback', () => {
    it('应渲染降级 UI 到容器', () => {
      document.body.innerHTML = '<div id="test-container"></div>';
      renderErrorFallback(
        { name: 'broken-app', entry: '/broken/', container: '#test-container', activeRule: '/broken' },
        '#test-container',
      );
      const el = document.getElementById('test-container')!;
      expect(el.innerHTML).toContain('broken-app');
      expect(el.innerHTML).toContain('加载失败');
      expect(el.innerHTML).toContain('重试');
    });

    it('容器不存在时应静默跳过', () => {
      expect(() => renderErrorFallback(
        { name: 'broken', entry: '/b/', container: '#nonexistent', activeRule: '/b' },
        '#nonexistent',
      )).not.toThrow();
    });
  });
});
