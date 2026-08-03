/**
 * loader 模块单元测试
 *
 * @path comm/effects/micro-kernel/__tests__/loader.test.ts
 * @author ydsz-team
 * @since 3.0.0
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchManifest, removeStylesheets } from '../src/loader';

import type { MicroAppConfig } from '@ydsz/micro-runtime';

describe('loader', () => {
  const config: MicroAppConfig = {
    name: 'loader-test',
    entry: '/test-app/',
    container: '#app',
    activeRule: '/test',
  };

  const mockManifest = {
    name: 'loader-test',
    entry: '/test-app/assets/index.js',
    css: ['/test-app/assets/style.css'],
    version: '1.0.0',
  };

  beforeEach(() => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockManifest,
    });
    document.head.innerHTML = '';
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('fetchManifest', () => {
    it('应正确拼接 manifest.json URL 并返回数据', async () => {
      const manifest = await fetchManifest(config.entry);
      expect(manifest).toEqual(mockManifest);
      expect(global.fetch).toHaveBeenCalledWith(
        '/test-app/manifest.json',
        expect.any(Object),
      );
    });

    it('HTTP 错误应抛出异常', async () => {
      (global.fetch as ReturnType<typeof vi.fn>).mockRejectedValueOnce(new Error('404'));
      await expect(fetchManifest('/bad/')).rejects.toThrow('404');
    });

    it('v3.1: 第二次请求相同 entry 应命中缓存（不重复 fetch）', async () => {
      // 使用独立 entry 避免跨用例缓存污染
      const cacheEntry = '/cache-test-app/';
      await fetchManifest(cacheEntry);
      await fetchManifest(cacheEntry);

      // fetch 只应被调用一次（第二次命中 manifestCache）
      expect(global.fetch).toHaveBeenCalledTimes(1);
    });
  });

  describe('removeStylesheets', () => {
    it('应注入带 data-micro-kernel-app 标记的 link 标签', () => {
      const link = document.createElement('link');
      link.rel = 'stylesheet';
      link.href = '/test.css';
      link.setAttribute('data-micro-kernel-app', 'test-x');
      document.head.appendChild(link);

      expect(
        document.querySelector('link[data-micro-kernel-app="test-x"]'),
      ).not.toBeNull();

      removeStylesheets('test-x');
      expect(
        document.querySelector('link[data-micro-kernel-app="test-x"]'),
      ).toBeNull();
    });
  });
});
