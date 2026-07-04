/**
 * @file 通用分页查询 composable
 * @description 封装列表页的分页、加载、查询参数等通用逻辑，配合 PageLayout 组件使用，大幅减少页面样板代码
 * @module composables/useTable
 *
 * 用法：
 * ```ts
 * const { loading, list, total, query, fetchData, handleQuery, resetQuery } =
 *   useTable(pageApi, { defaultSize: 20 })
 * ```
 */
import { ref, reactive, type Ref } from 'vue'

/** 分页查询参数基类 */
export interface UseTableQuery {
  /** 当前页码（从 1 开始） */
  page: number
  /** 每页大小 */
  size: number
  [key: string]: unknown
}

export interface UseTableOptions<Q extends UseTableQuery> {
  /** 默认分页大小 */
  defaultSize?: number
  /** 初始查询参数 */
  defaultQuery?: Partial<Q>
}

/**
 * 通用分页查询 composable
 *
 * @param fetcher - 数据拉取函数，接收 query 返回 PageResult
 * @param options - 初始化选项（默认分页大小、初始查询参数）
 * @returns 包含 loading/list/total/query 与分页操作方法的对象
 */
export function useTable<Q extends UseTableQuery>(
  fetcher: (query: Q) => Promise<PageResult<unknown>>,
  options: UseTableOptions<Q> = {},
) {
  /** 加载中标志 */
  const loading = ref(false)
  /** 当前列表数据 */
  const list = ref<unknown[]>([]) as Ref<unknown[]>
  /** 总条数（用于分页器显示） */
  const total = ref(0)

  /** 查询参数（响应式，绑定到搜索表单） */
  const query = reactive<Q>({
    ...(options.defaultQuery as Q),
    page: (options.defaultQuery?.page ?? 1) as Q['page'],
    size: (options.defaultQuery?.size ?? options.defaultSize ?? 10) as Q['size'],
  } as unknown as Q)

  /** 拉取数据（兼容 ApiResponse<PageResult> 与 PageResult 两种返回结构） */
  async function fetchData(): Promise<void> {
    loading.value = true
    try {
      const res = await fetcher(query as unknown as Q) as {
        data?: { list?: unknown[]; total?: number }
        list?: unknown[]
        total?: number
      }
      list.value = res?.data?.list ?? res?.list ?? []
      total.value = res?.data?.total ?? res?.total ?? 0
    } finally {
      loading.value = false
    }
  }

  /** 触发查询：重置 page 为 1 后拉取 */
  function handleQuery(): Promise<void> {
    query.page = 1
    return fetchData()
  }

  /**
   * 重置查询条件
   * @param defaults - 重置后保留的默认值（可选）
   */
  function resetQuery(defaults?: Partial<Q>): Promise<void> {
    Object.keys(query).forEach((k) => {
      if (k === 'page' || k === 'size') return
      if (defaults && k in defaults) {
        ;(query as Record<string, unknown>)[k] = defaults[k as keyof Q]
      } else {
        ;(query as Record<string, unknown>)[k] = undefined
      }
    })
    query.page = 1
    return fetchData()
  }

  /** 分页器变化时拉取（page/size 已由 vxe-table 同步到 query） */
  function handlePageChange(): Promise<void> {
    return fetchData()
  }

  return {
    loading,
    list,
    total,
    query,
    fetchData,
    handleQuery,
    resetQuery,
    handlePageChange,
  }
}
