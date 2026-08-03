/**
 * i18n 国际化模块单元测试
 *
 * F2 国际化按需加载改造：
 * - 命名空间粒度映射表构建
 * - 路由级命名空间按需加载与去重
 * - 空闲预加载（requestIdleCallback / setTimeout 回退）
 *
 * @path comm/locales/__tests__/i18n.test.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { NamespacedLocalesMap, SupportedLanguagesType } from '../src/typing';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// Mock @ydsz-core/composables 以隔离 @ydsz-core/shared 的预存在导入问题，
// 仅暴露 i18n.ts 实际使用的 setSimpleLocale。
vi.mock('@ydsz-core/composables', () => ({
  useSimpleLocale: () => ({
    currentLocale: { value: 'zh-CN' },
    setSimpleLocale: () => {},
    $t: { value: (key: string) => key },
  }),
}));

import {
  i18n,
  loadNamespaceMessages,
  loadNamespacedLocalesMap,
  preloadLocaleOnIdle,
} from '../src/i18n';

const REGEXP = /\.\/langs\/([^/]+)\/(.*)\.json$/;

function makeModules(
  entries: Record<string, () => Promise<{ default: Record<string, unknown> }>>,
): Record<string, () => Promise<unknown>> {
  return entries;
}

describe('i18n: F2 命名空间按需加载', () => {
  describe('loadNamespacedLocalesMap', () => {
    it('应按 locale → namespace 两级结构构建映射表', () => {
      const modules = makeModules({
        './langs/zh-CN/common.json': () =>
          Promise.resolve({ default: { hello: '你好' } }),
        './langs/zh-CN/page.json': () =>
          Promise.resolve({ default: { title: '页面' } }),
        './langs/en-US/common.json': () =>
          Promise.resolve({ default: { hello: 'Hello' } }),
      });

      const map = loadNamespacedLocalesMap(REGEXP, modules);

      expect(map['zh-CN']).toBeDefined();
      expect(map['zh-CN']?.common).toBeTypeOf('function');
      expect(map['zh-CN']?.page).toBeTypeOf('function');
      expect(map['en-US']?.common).toBeTypeOf('function');
      // en-US 没有 page
      expect(map['en-US']?.page).toBeUndefined();
    });

    it('不匹配正则的路径应被忽略', () => {
      const modules = makeModules({
        './langs/zh-CN/common.json': () =>
          Promise.resolve({ default: { hello: '你好' } }),
        './README.md': () => Promise.resolve({ default: {} }),
      });

      const map = loadNamespacedLocalesMap(REGEXP, modules);
      expect(map['zh-CN']).toBeDefined();
      expect(Object.keys(map['zh-CN']!)).toEqual(['common']);
    });
  });

  describe('loadNamespaceMessages', () => {
    let map: NamespacedLocalesMap;

    beforeEach(() => {
      const modules = makeModules({
        './langs/zh-CN/common.json': () =>
          Promise.resolve({ default: { hello: '你好' } }),
        './langs/zh-CN/page.json': () =>
          Promise.resolve({ default: { title: '页面标题' } }),
      });
      map = loadNamespacedLocalesMap(REGEXP, modules);
      // 清空 i18n 已有词条，避免测试间污染
      i18n.global.setLocaleMessage('zh-CN', {});
    });

    it('应仅加载指定命名空间并合并入 i18n', async () => {
      await loadNamespaceMessages('zh-CN', ['common'], map);

      const messages = i18n.global.getLocaleMessage('zh-CN') as Record<
        string,
        unknown
      >;
      expect(messages.common).toEqual({ hello: '你好' });
      // page 未加载
      expect(messages.page).toBeUndefined();
    });

    it('应并行加载多个命名空间', async () => {
      await loadNamespaceMessages('zh-CN', ['common', 'page'], map);

      const messages = i18n.global.getLocaleMessage('zh-CN') as Record<
        string,
        unknown
      >;
      expect(messages.common).toEqual({ hello: '你好' });
      expect(messages.page).toEqual({ title: '页面标题' });
    });

    it('请求不存在的命名空间应静默跳过', async () => {
      await loadNamespaceMessages('zh-CN', ['common', 'nonexistent'], map);

      const messages = i18n.global.getLocaleMessage('zh-CN') as Record<
        string,
        unknown
      >;
      expect(messages.common).toBeDefined();
      expect(messages.nonexistent).toBeUndefined();
    });

    it('目标语种不存在于映射表时应安全返回', async () => {
      await expect(
        loadNamespaceMessages('fr-FR' as SupportedLanguagesType, ['common'], map),
      ).resolves.toBeUndefined();
    });
  });

  describe('preloadLocaleOnIdle', () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('无 requestIdleCallback 时应通过 setTimeout 回退执行', async () => {
      // 模拟无 requestIdleCallback 环境
      const originalRIC = (
        globalThis as { requestIdleCallback?: unknown }
      ).requestIdleCallback;
      // @ts-expect-error 测试需要删除全局 API
      delete (globalThis as Record<string, unknown>).requestIdleCallback;

      const loader = vi.fn().mockResolvedValue({
        default: { greeting: 'Hello' },
      });
      const targetMap = { 'en-US': loader } as unknown as Record<
        string,
        () => Promise<{ default: Record<string, string> }>
      >;

      // 确保 en-US 未被标记为已加载（清空 i18n 词条）
      i18n.global.setLocaleMessage('en-US', {});

      preloadLocaleOnIdle('en-US', targetMap, { timeout: 1000 });

      // setTimeout 尚未触发
      expect(loader).not.toHaveBeenCalled();

      // 推进定时器
      vi.advanceTimersByTime(1000);
      await vi.waitFor(() => expect(loader).toHaveBeenCalled());

      // 恢复原始 requestIdleCallback
      if (originalRIC) {
        (globalThis as { requestIdleCallback?: unknown }).requestIdleCallback =
          originalRIC;
      }
    });

    it('加载成功后应写入 i18n 词条', async () => {
      const originalRIC = (
        globalThis as { requestIdleCallback?: unknown }
      ).requestIdleCallback;
      // @ts-expect-error 测试需要删除全局 API
      delete (globalThis as Record<string, unknown>).requestIdleCallback;

      // 使用 zh-CN 避免与上一个测试的 loadedLocales 状态冲突
      i18n.global.setLocaleMessage('zh-CN', {});

      const loader = vi.fn().mockResolvedValue({
        default: { greeting: '你好' },
      });
      const targetMap = { 'zh-CN': loader } as unknown as Record<
        string,
        () => Promise<{ default: Record<string, string> }>
      >;

      preloadLocaleOnIdle('zh-CN', targetMap, { timeout: 500 });
      vi.advanceTimersByTime(500);
      await vi.waitFor(() => expect(loader).toHaveBeenCalled());

      // 等待 Promise 微任务完成
      await vi.waitFor(() => {
        const msgs = i18n.global.getLocaleMessage('zh-CN') as Record<
          string,
          unknown
        >;
        expect(msgs?.greeting).toBe('你好');
      });

      if (originalRIC) {
        (globalThis as { requestIdleCallback?: unknown }).requestIdleCallback =
          originalRIC;
      }
    });

    it('loader 不存在时应调用 onLoaded 而不抛错', () => {
      const originalRIC = (
        globalThis as { requestIdleCallback?: unknown }
      ).requestIdleCallback;
      // @ts-expect-error 测试需要删除全局 API
      delete (globalThis as Record<string, unknown>).requestIdleCallback;

      // 使用 fr-FR 避免命中已加载缓存
      const onLoaded = vi.fn();
      preloadLocaleOnIdle('fr-FR' as SupportedLanguagesType, {}, {
        timeout: 0,
        onLoaded,
      });
      vi.advanceTimersByTime(0);
      expect(onLoaded).toHaveBeenCalled();

      if (originalRIC) {
        (globalThis as { requestIdleCallback?: unknown }).requestIdleCallback =
          originalRIC;
      }
    });
  });
});
