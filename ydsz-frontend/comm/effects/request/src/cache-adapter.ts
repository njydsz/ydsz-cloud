/**
 * cache-adapter 模块 — 请求级 SWR 缓存（stale-while-revalidate）
 *
 * @path comm\effects\request\src\cache-adapter.ts
 * @author ydsz-team
 * @since 1.1.0
 *
 * @remarks
 * 以装饰器方式为 GET 请求注入 SWR 缓存，不改动 RequestClient 核心：
 * - 首次请求后缓存响应
 * - 缓存未过期（staleTime）：直接返回缓存并异步后台刷新（revalidate）
 * - 缓存已过期：返回旧缓存（stale）同时发起新请求，命中后更新缓存
 * - TTL（maxAge）：超过后强制重新请求，不返回旧数据
 * - 仅缓存 GET 请求，避免数据写请求被污染
 *
 * 典型应用：字典、菜单、配置等低频变更数据。
 */

/** SWR 缓存配置 */
export interface SwrCacheOptions {
  /** 缓存有效期 ms（超过后不再返回旧数据，强制刷新），默认 10 分钟 */
  maxAge?: number;
  /** 过期但可复用旧数据的时间 ms（SWR 窗口），默认 5 分钟 */
  staleTime?: number;
  /** 是否启用，默认 true */
  enabled?: boolean;
  /** 自定义缓存 key（默认 url + 序列化 params） */
  keyFn?: (url: string, params?: any) => string;
  /** 是否缓存失败的请求，默认 false */
  cacheError?: boolean;
  /** 缓存容量上限，默认 100 */
  maxEntries?: number;
}

interface CacheEntry<T> {
  data: T;
  /** 数据获取时间 */
  fetchedAt: number;
  /** 是否已失效（需要后台刷新） */
  stale: boolean;
  /** 进行中的后台刷新 promise */
  inflight?: Promise<T>;
}

const DEFAULT_MAX_AGE = 10 * 60 * 1000;
const DEFAULT_STALE_TIME = 5 * 60 * 1000;
const DEFAULT_MAX_ENTRIES = 100;

/** 模块级缓存 map */
const cacheMap = new Map<string, CacheEntry<any>>();

/** LRU 淘汰：删除最旧条目 */
function evict(maxEntries: number) {
  if (cacheMap.size <= maxEntries) return;
  const keys = [...cacheMap.keys()];
  const toRemove = cacheMap.size - maxEntries;
  for (let i = 0; i < toRemove; i += 1) {
    cacheMap.delete(keys[i]);
  }
}

/** 生成缓存 key */
function defaultKeyFn(url: string, params?: any): string {
  if (!params) return url;
  try {
    return `${url}?${JSON.stringify(params)}`;
  } catch {
    return url;
  }
}

/** 清空全部缓存（数据变更时业务侧调用） */
export function clearSwrCache(key?: string) {
  if (key) {
    cacheMap.delete(key);
  } else {
    cacheMap.clear();
  }
}

/**
 * 为请求函数包装 SWR 缓存
 *
 * @param fetcher - 原始请求函数（返回 Promise）
 * @param options - SWR 配置
 * @returns 包装后的请求函数
 *
 * @example
 * ```ts
 * const cachedGetDict = withSwrCache(
 *   (url, params) => requestClient.get(url, { params }),
 *   { maxAge: 10 * 60 * 1000 },
 * );
 * ```
 */
export function withSwrCache<T = any>(
  fetcher: (url: string, params?: any, ...rest: any[]) => Promise<T>,
  options: SwrCacheOptions = {},
): (url: string, params?: any, ...rest: any[]) => Promise<T> {
  const {
    maxAge = DEFAULT_MAX_AGE,
    staleTime = DEFAULT_STALE_TIME,
    enabled = true,
    keyFn = defaultKeyFn,
    cacheError = false,
    maxEntries = DEFAULT_MAX_ENTRIES,
  } = options;

  return async (url, params, ...rest) => {
    if (!enabled) {
      return fetcher(url, params, ...rest);
    }

    const key = keyFn(url, params);
    const now = Date.now();
    const entry = cacheMap.get(key);

    // 无缓存：直接请求并写入
    if (!entry) {
      try {
        const data = await fetcher(url, params, ...rest);
        cacheMap.set(key, {
          data,
          fetchedAt: now,
          stale: false,
        });
        evict(maxEntries);
        return data;
      } catch (error) {
        if (cacheError) {
          cacheMap.set(key, {
            data: error,
            fetchedAt: now,
            stale: false,
          });
        }
        throw error;
      }
    }

    const age = now - entry.fetchedAt;

    // 缓存完全过期（超过 maxAge）：返回旧数据（stale）同时后台刷新
    if (age > maxAge) {
      const staleData = entry.data;
      void revalidate(key, url, params, rest, fetcher);
      return staleData;
    }

    // SWR 窗口内（超过 staleTime 未超 maxAge）：返回旧数据 + 后台刷新
    if (age > staleTime) {
      const staleData = entry.data;
      void revalidate(key, url, params, rest, fetcher);
      return staleData;
    }

    // 新鲜缓存：直接返回
    return entry.data;
  };
}

/** 后台刷新：并发去重 */
function revalidate<T>(
  key: string,
  url: string,
  params: any,
  rest: any[],
  fetcher: (url: string, params?: any, ...rest: any[]) => Promise<T>,
) {
  const entry = cacheMap.get(key);
  if (entry?.inflight) return entry.inflight;

  const promise = fetcher(url, params, ...rest)
    .then((data) => {
      cacheMap.set(key, {
        data,
        fetchedAt: Date.now(),
        stale: false,
      });
      return data;
    })
    .catch(() => {
      // 后台刷新失败：保留旧缓存，不抛错
      return cacheMap.get(key)?.data;
    })
    .finally(() => {
      const current = cacheMap.get(key);
      if (current) delete current.inflight;
    });

  const entryWithInflight = cacheMap.get(key);
  if (entryWithInflight) {
    entryWithInflight.inflight = promise;
  }
  return promise;
}

/** 当前缓存条目数（测试/调试用） */
export function getSwrCacheSize(): number {
  return cacheMap.size;
}
