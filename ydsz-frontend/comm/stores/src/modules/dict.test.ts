/**
 * dict store 单元测试
 *
 * @path comm\stores\src\modules\dict.test.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { createPinia, setActivePinia } from 'pinia';

import { useDictStore, type DictItem } from './dict';

function makeItem(overrides: Partial<DictItem> = {}): DictItem {
  return {
    id: '1',
    typeCode: 'test_type',
    itemCode: 'A',
    itemText: '选项A',
    itemValue: 'a',
    sort: 1,
    status: 1,
    ...overrides,
  };
}

describe('useDictStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('未注入 loader 时 ensureLoaded 返回空数组', async () => {
    const store = useDictStore();
    const items = await store.ensureLoaded('test_type');
    expect(items).toEqual([]);
  });

  it('ensureLoaded 拉取数据并写入缓存', async () => {
    const store = useDictStore();
    const mockLoader = vi.fn().mockResolvedValue([makeItem()]);
    store.setDictLoader(mockLoader);

    const items = await store.ensureLoaded('test_type');
    expect(items).toHaveLength(1);
    expect(store.getItems('test_type')).toHaveLength(1);
    // 二次调用命中缓存，loader 只被调用一次
    await store.ensureLoaded('test_type');
    expect(mockLoader).toHaveBeenCalledTimes(1);
  });

  it('并发调用去重，loader 只执行一次', async () => {
    const store = useDictStore();
    const mockLoader = vi.fn().mockResolvedValue([makeItem()]);
    store.setDictLoader(mockLoader);

    await Promise.all([
      store.ensureLoaded('dup_type'),
      store.ensureLoaded('dup_type'),
      store.ensureLoaded('dup_type'),
    ]);
    expect(mockLoader).toHaveBeenCalledTimes(1);
  });

  it('invalidate 清除指定或全部缓存', async () => {
    const store = useDictStore();
    store.setDictLoader(vi.fn().mockResolvedValue([makeItem()]));

    await store.ensureLoaded('type_a');
    await store.ensureLoaded('type_b');

    store.invalidate('type_a');
    expect(store.getItems('type_a')).toHaveLength(0);
    expect(store.getItems('type_b')).toHaveLength(1);

    store.invalidate();
    expect(store.getItems('type_b')).toHaveLength(0);
  });

  it('TTL 过期后重新请求', async () => {
    const store = useDictStore();
    const mockLoader = vi
      .fn()
      .mockResolvedValue([makeItem({ itemValue: 'v1' })]);
    store.setDictLoader(mockLoader);

    await store.ensureLoaded('ttl_type');
    expect(mockLoader).toHaveBeenCalledTimes(1);

    // 手动使缓存过期
    store.cache.ttl_type.expiresAt = Date.now() - 1000;
    await store.ensureLoaded('ttl_type');
    expect(mockLoader).toHaveBeenCalledTimes(2);
  });

  it('prune 清理过期缓存', async () => {
    const store = useDictStore();
    store.setDictLoader(vi.fn().mockResolvedValue([makeItem()]));
    await store.ensureLoaded('prune_type');

    store.cache.prune_type.expiresAt = Date.now() - 1000;
    store.prune();
    expect(store.cache.prune_type).toBeUndefined();
  });

  it('getItems 对过期缓存返回空数组', async () => {
    const store = useDictStore();
    store.setDictLoader(vi.fn().mockResolvedValue([makeItem()]));
    await store.ensureLoaded('expired_type');

    store.cache.expired_type.expiresAt = Date.now() - 1000;
    expect(store.getItems('expired_type')).toHaveLength(0);
  });
});
