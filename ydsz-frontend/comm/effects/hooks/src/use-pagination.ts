/**
 * use-pagination 组合式函数
 *
 * @path comm\effects\hooks\src\use-pagination.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Ref } from 'vue';

import { computed, ref, unref } from 'vue';

/**
 * Paginates an array of items
 * @param list The array to paginate
 * @param pageNo The current page number (1-based)
 * @param pageSize Number of items per page
 * @returns Paginated array slice
 * @throws {Error} If pageNo or pageSize are invalid
 */
function pagination<T = any>(list: T[], pageNo: number, pageSize: number): T[] {
  if (pageNo < 1) throw new Error('Page number must be positive');
  if (pageSize < 1) throw new Error('Page size must be positive');

  const offset = (pageNo - 1) * Number(pageSize);
  const ret =
    offset + pageSize >= list.length
      ? list.slice(offset)
      : list.slice(offset, offset + pageSize);
  return ret;
}

/**
 * 对已全量加载到内存的列表做**前端分页**。
 *
 * @remarks
 * 适用于数据量可控、一次性拉回全部数据的场景；需要服务端分页时不要使用本 Hook。
 *
 * 副作用与生命周期：仅创建 `ref` / `computed`，不注册侦听器、定时器或请求，
 * 因此可在 `setup` 之外调用，也无需手动清理。
 *
 * 注意点：
 * - 源列表 `list` 变化时 `paginationList` 与 `total` 会自动重算，
 *   但 `currentPage` **不会自动回到第 1 页**；若列表变短可能停留在空页，需要业务侧自行重置；
 * - `setPageSize` 会把当前页强制重置为 1，避免出现越界的中间态；
 * - 返回值中未暴露 `currentPage` / `totalPages`，页码只能通过 `setCurrentPage` 单向设置。
 *
 * @param list - 源数据列表的响应式引用；内容变化会驱动分页结果重新计算
 * @param pageSize - 初始每页条数，必须为正整数
 * @returns 分页状态与操作方法：`paginationList` 为当前页数据切片，`total` 为源列表总条数，
 * `setCurrentPage` 跳转页码，`setPageSize` 修改每页条数并回到第一页
 *
 * @throws {Error} `setCurrentPage` 传入的页码小于 1 或超过总页数时抛出 `'Invalid page number'`
 * @throws {Error} `setPageSize` 传入非正数时抛出 `'Page size must be positive'`
 *
 * @example
 * ```ts
 * const list = ref([1, 2, 3, 4, 5]);
 * const { paginationList, total, setCurrentPage } = usePagination(list, 2);
 * setCurrentPage(2); // paginationList.value === [3, 4]
 * ```
 */
export function usePagination<T = any>(list: Ref<T[]>, pageSize: number) {
  const currentPage = ref(1);
  const pageSizeRef = ref(pageSize);

  const totalPages = computed(() =>
    Math.ceil(unref(list).length / unref(pageSizeRef)),
  );

  const paginationList = computed(() => {
    return pagination(unref(list), unref(currentPage), unref(pageSizeRef));
  });

  const total = computed(() => {
    return unref(list).length;
  });

  function setCurrentPage(page: number) {
    if (page < 1 || page > unref(totalPages)) {
      throw new Error('Invalid page number');
    }
    currentPage.value = page;
  }

  function setPageSize(pageSize: number) {
    if (pageSize < 1) {
      throw new Error('Page size must be positive');
    }
    pageSizeRef.value = pageSize;
    // Reset to first page to prevent invalid state
    currentPage.value = 1;
  }

  return { setCurrentPage, total, setPageSize, paginationList };
}
