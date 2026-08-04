/**
 * cache-adapter 单元测试
 *
 * @path comm\effects\request\src\cache-adapter.test.ts
 * @author ydsz-team
 * @since 1.1.0
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { clearSwrCache, getSwrCacheSize, withSwrCache } from './cache-adapter';

describe('withSwrCache', () => {
  beforeEach(() => {
    clearSwrCache();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('首次调用请求并缓存', async () => {
    const fetcher = vi.fn().mockResolvedValue('data-1');
    const cached = withSwrCache(fetcher);

    const result = await cached('/api/x');
    expect(result).toBe('data-1');
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(getSwrCacheSize()).toBe(1);
  });

  it('新鲜缓存直接命中，不发请求', async () => {
    const fetcher = vi.fn().mockResolvedValue('data-1');
    const cached = withSwrCache(fetcher, { maxAge: 60_000, staleTime: 60_000 });

    await cached('/api/x');
    await cached('/api/x');
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it('SWR 窗口内返回旧数据并后台刷新', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce('old')
      .mockResolvedValueOnce('new');
    const cached = withSwrCache(fetcher, { maxAge: 60_000, staleTime: 1000 });

    await cached('/api/x');
    vi.advanceTimersByTime(2000);

    const result = await cached('/api/x');
    expect(result).toBe('old'); // 先返回旧数据
    await vi.advanceTimersByTimeAsync(0); // 等待后台刷新完成
    const result2 = await cached('/api/x');
    expect(result2).toBe('new'); // 缓存已更新
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it('超过 maxAge 仍返回旧数据并刷新', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce('old')
      .mockResolvedValueOnce('new');
    const cached = withSwrCache(fetcher, { maxAge: 1000, staleTime: 500 });

    await cached('/api/x');
    vi.advanceTimersByTime(2000);

    const result = await cached('/api/x');
    expect(result).toBe('old');
    await vi.advanceTimersByTimeAsync(0);
    expect(await cached('/api/x')).toBe('new');
  });

  it('不同 params 使用不同缓存', async () => {
    const fetcher = vi
      .fn()
      .mockImplementation((_url, params) => Promise.resolve(`data-${params.id}`));
    const cached = withSwrCache(fetcher, { maxAge: 60_000, staleTime: 60_000 });

    await cached('/api/x', { id: 1 });
    await cached('/api/x', { id: 2 });
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it('disabled 时直接透传请求', async () => {
    const fetcher = vi.fn().mockResolvedValue('data');
    const cached = withSwrCache(fetcher, { enabled: false });

    await cached('/api/x');
    await cached('/api/x');
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it('后台刷新失败保留旧缓存', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce('old')
      .mockRejectedValueOnce(new Error('network'));
    const cached = withSwrCache(fetcher, { maxAge: 60_000, staleTime: 1000 });

    await cached('/api/x');
    vi.advanceTimersByTime(2000);
    await cached('/api/x'); // 触发后台刷新（失败）
    await vi.advanceTimersByTimeAsync(0);
    // 缓存仍是旧数据
    expect(await cached('/api/x')).toBe('old');
  });

  it('clearSwrCache 清空缓存', async () => {
    const fetcher = vi.fn().mockResolvedValue('data');
    const cached = withSwrCache(fetcher, { maxAge: 60_000, staleTime: 60_000 });

    await cached('/api/x');
    clearSwrCache();
    expect(getSwrCacheSize()).toBe(0);
    await cached('/api/x');
    expect(fetcher).toHaveBeenCalledTimes(2);
  });
});
