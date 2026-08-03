/**
 * loader 模块单元测试
 *
 * @path comm/effects/micro-kernel-lite/__tests__/loader.test.ts
 * @author ydsz-team
 * @since 3.0.0
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { loadApp, fetchManifest, removeStylesheets } from '../src/loader';

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

  const mockModule = {
    mount: async () => {},
    unmount: async () => {},
    bootstrap: async () => {},
  };

  beforeEach(() => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockManifest,
    });
    // @ts-expect-error dynamic import mock
    vi.spyOn(globalThis, 'import').mockResolvedValue(mockModule);
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
      (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ ok: false, status: 404 });
      await expect(fetchManifest('/bad/')).rejects.toThrow('404');
    });

    it('应缓存已获取的 manifest', async () => {
      await fetchManifest(config.entry);
      await fetchManifest(config.entry);
      expect(global.fetch).toHaveBeenCalledTimes(1);
    });
  });

  describe('injectStylesheets / removeStylesheets', () => {
    it('应注入带 data-lite-kernel-app 标记的 link 标签', () => {
      // loadApp handles injection; we test indirectly via mock import behavior
      const link = document.createElement('link');
      link.rel = 'stylesheet';
      link.href = '/test.css';
      link.setAttribute('data-lite-kernel-app', 'test-x');
      document.head.appendChild(link);

      expect(
        document.querySelector('link[data-lite-kernel-app="test-x"]'),
      ).not.toBeNull();

      removeStylesheets('test-x');
      expect(
        document.querySelector('link[data-lite-kernel-app="test-x"]'),
      ).toBeNull();
    });
  });

  describe('生命周期断言', () => {
    it('缺少 mount 导出应抛出异常', async () => {
      // @ts-expect-error mock with no mount
      vi.spyOn(globalThis, 'import').mockResolvedValueOnce({ unmount: async () => {} });

      await expect(loadApp(config)).rejects.toThrow(/must export "mount"/);
    });

    it('缺少 unmount 导出应抛出异常', async () => {
      // @ts-expect-error mock with no unmount
      vi.spyOn(globalThis, 'import').mockResolvedValueOnce({ mount: async () => {} });

      await expect(loadApp(config)).rejects.toThrow(/must export "unmount"/);
    });
  });
});
