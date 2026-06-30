/**
 * 通用分页查询 composable
 *
 * 封装列表页的分页、加载、查询参数等通用逻辑，
 * 配合 PageLayout 组件使用，大幅减少页面样板代码。
 */
import { ref, reactive, type Ref } from 'vue'

export interface UseTableQuery {
  page: number
  size: number
  [key: string]: unknown
}

export interface UseTableOptions<Q extends UseTableQuery> {
  /** 默认分页大小 */
  defaultSize?: number
  /** 初始查询参数 */
  defaultQuery?: Partial<Q>
}

export function useTable<Q extends UseTableQuery>(
  fetcher: (query: Q) => Promise<PageResult<any>>,
  options: UseTableOptions<Q> = {},
) {
  const loading = ref(false)
  const list = ref<any[]>([]) as Ref<any[]>
  const total = ref(0)

  const query = reactive<Q>({
    page: 1,
    size: options.defaultSize ?? 10,
    ...(options.defaultQuery as Q),
  } as Q)

  async function fetchData(): Promise<void> {
    loading.value = true
    try {
      const res: any = await fetcher(query)
      list.value = res?.data?.list ?? res?.list ?? []
      total.value = res?.data?.total ?? res?.total ?? 0
    } finally {
      loading.value = false
    }
  }

  function handleQuery(): Promise<void> {
    query.page = 1
    return fetchData()
  }

  function resetQuery(defaults?: Partial<Q>): Promise<void> {
    Object.keys(query).forEach((k) => {
      const key = k as keyof Q
      if (k === 'page' || k === 'size') return
      if (defaults && k in defaults) {
        ;(query as any)[key] = defaults[k]
      } else {
        ;(query as any)[key] = undefined
      }
    })
    query.page = 1
    return fetchData()
  }

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
