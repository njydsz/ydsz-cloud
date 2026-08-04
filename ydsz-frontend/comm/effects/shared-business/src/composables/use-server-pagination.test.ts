/**
 * use-server-pagination 单元测试
 *
 * @path comm\effects\shared-business\src\composables\use-server-pagination.test.ts
 * @author ydsz-team
 * @since 1.1.0
 */
import { describe, expect, it, vi } from 'vitest';

import { nextTick } from 'vue';

import { useServerPagination } from './use-server-pagination';

function createFetcher(items: any[] = [], total = 0) {
  return vi.fn().mockImplementation(async (query: any) => {
    return { items: items.slice((query.pageNum - 1) * query.pageSize, query.pageNum * query.pageSize), total };
  });
}

describe('useServerPagination', () => {
  it('首次加载自动请求并携带分页参数', async () => {
    const fetcher = createFetcher([{ id: 1 }], 1);
    const { items, total, loading } = useServerPagination(fetcher, {});

    await nextTick();
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalled());

    expect(fetcher).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10 });
    expect(total.value).toBe(1);
    expect(items.value).toHaveLength(1);
    expect(loading.value).toBe(false);
  });

  it('changePage 更新页码并重新请求', async () => {
    const fetcher = createFetcher([{ id: 1 }, { id: 2 }], 2);
    const pagination = useServerPagination(fetcher, {}, { pageSize: 1 });

    await nextTick();
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1));

    pagination.changePage(2);
    await nextTick();
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledTimes(2));

    expect(fetcher).toHaveBeenLastCalledWith({ pageNum: 2, pageSize: 1 });
    expect(pagination.items.value).toHaveLength(1);
  });

  it('search 重置到第一页', async () => {
    const fetcher = createFetcher([], 0);
    const pagination = useServerPagination(fetcher, {}, { pageSize: 10 });

    await nextTick();
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1));

    pagination.pageNum.value = 3;
    pagination.search();
    await nextTick();
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledTimes(2));

    expect(pagination.pageNum.value).toBe(1);
  });

  it('changePageSize 重置到第一页', async () => {
    const fetcher = createFetcher([], 0);
    const pagination = useServerPagination(fetcher, {}, { pageSize: 10 });

    await nextTick();
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1));

    pagination.pageNum.value = 5;
    pagination.changePageSize(20);
    await nextTick();
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledTimes(2));

    expect(pagination.pageNum.value).toBe(1);
    expect(pagination.pageSize.value).toBe(20);
  });

  it('reset 恢复初始分页状态', async () => {
    const fetcher = createFetcher([], 0);
    const pagination = useServerPagination(fetcher, {}, { pageSize: 10 });

    await nextTick();
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalled());

    pagination.pageNum.value = 4;
    pagination.pageSize.value = 50;
    pagination.total.value = 100;
    pagination.reset();

    expect(pagination.pageNum.value).toBe(1);
    expect(pagination.pageSize.value).toBe(10);
    expect(pagination.total.value).toBe(0);
    expect(pagination.items.value).toEqual([]);
  });

  it('immediate=false 时不自动请求', async () => {
    const fetcher = createFetcher([], 0);
    const pagination = useServerPagination(fetcher, {}, { immediate: false });

    await nextTick();
    expect(fetcher).not.toHaveBeenCalled();

    await pagination.fetchData();
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it('合并外部响应式查询参数', async () => {
    const fetcher = createFetcher([], 0);
    const params = { keyword: '测试' };
    const pagination = useServerPagination(fetcher, params as any);

    await nextTick();
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalled());

    expect(fetcher).toHaveBeenCalledWith({
      keyword: '测试',
      pageNum: 1,
      pageSize: 10,
    });
  });
});
