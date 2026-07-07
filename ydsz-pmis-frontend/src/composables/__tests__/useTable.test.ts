/**
 * @file useTable.test.ts
 * @description 测试 useTable 分页查询 composable
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// ========== Mock 依赖 ==========
// useTable 内部调用 useI18n() 与 ElMessage，测试中需提供 mock 避免上下文缺失
vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  },
}))

// ========== 导入被测试模块 ==========
import { useTable } from '@/composables/useTable'
import type { UseTableQuery } from '@/composables/useTable'

interface TestQuery extends UseTableQuery {
  page: number
  size: number
  keyword?: string
}

describe('useTable', () => {
  let mockFetcher: ReturnType<typeof vi.fn>

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('fetchData', () => {
    it('应调用 fetcher 并更新 list 和 total（兼容 ApiResponse 包装）', async () => {
      mockFetcher = vi.fn().mockResolvedValue({
        data: { list: [{ id: 1 }, { id: 2 }], total: 2 },
      })

      const { list, total, loading, fetchData } = useTable<TestQuery>(
        mockFetcher,
        { defaultSize: 10 },
      )

      await fetchData()

      expect(mockFetcher).toHaveBeenCalledTimes(1)
      expect(list.value).toEqual([{ id: 1 }, { id: 2 }])
      expect(total.value).toBe(2)
      expect(loading.value).toBe(false)
    })

    it('应兼容直接返回 PageResult 的 fetcher（无 data 包装）', async () => {
      mockFetcher = vi.fn().mockResolvedValue({
        list: [{ id: 3 }, { id: 4 }],
        total: 100,
      })

      const { list, total, fetchData } = useTable<TestQuery>(mockFetcher)

      await fetchData()

      expect(list.value).toEqual([{ id: 3 }, { id: 4 }])
      expect(total.value).toBe(100)
    })

    it('fetchData 期间 loading 应为 true', async () => {
      let resolvePromise: (value: unknown) => void
      mockFetcher = vi.fn().mockImplementation(
        () =>
          new Promise((resolve) => {
            resolvePromise = resolve
          }),
      )

      const { loading, fetchData } = useTable<TestQuery>(mockFetcher)

      const fetchPromise = fetchData()
      expect(loading.value).toBe(true)

      resolvePromise!({ data: { list: [], total: 0 } })
      await fetchPromise
      expect(loading.value).toBe(false)
    })

    it('fetcher 抛出异常时 loading 应恢复为 false', async () => {
      mockFetcher = vi.fn().mockRejectedValue(new Error('Network error'))

      const { loading, fetchData } = useTable<TestQuery>(mockFetcher)

      await expect(fetchData()).rejects.toThrow('Network error')
      expect(loading.value).toBe(false)
    })
  })

  describe('handleQuery', () => {
    it('应将 page 重置为 1 并拉取数据', async () => {
      mockFetcher = vi.fn().mockResolvedValue({
        data: { list: [], total: 0 },
      })

      const { query, handleQuery } = useTable<TestQuery>(mockFetcher, {
        defaultSize: 20,
      })

      query.page = 5
      await handleQuery()

      expect(query.page).toBe(1)
      expect(mockFetcher).toHaveBeenCalledTimes(1)
    })
  })

  describe('resetQuery', () => {
    it('应清除查询条件（保留 page=1 和 size）并重新拉取', async () => {
      mockFetcher = vi.fn().mockResolvedValue({
        data: { list: [], total: 0 },
      })

      const { query, resetQuery } = useTable<TestQuery>(mockFetcher, {
        defaultSize: 10,
      })

      query.keyword = 'test'
      query.page = 3

      await resetQuery()

      expect(query.page).toBe(1)
      expect(query.keyword).toBeUndefined()
      expect(query.size).toBe(10)
      expect(mockFetcher).toHaveBeenCalledTimes(1)
    })

    it('应保留 defaults 中指定的查询条件', async () => {
      mockFetcher = vi.fn().mockResolvedValue({
        data: { list: [], total: 0 },
      })

      const { query, resetQuery } = useTable<TestQuery>(mockFetcher)

      query.keyword = 'old'
      query.page = 3

      await resetQuery({ keyword: 'preserved' } as Partial<TestQuery>)

      expect(query.page).toBe(1)
      expect(query.keyword).toBe('preserved')
    })
  })

  describe('handlePageChange', () => {
    it('应调用 fetchData 拉取当前页数据', async () => {
      mockFetcher = vi.fn().mockResolvedValue({
        data: { list: [], total: 0 },
      })

      const { handlePageChange } = useTable<TestQuery>(mockFetcher)

      await handlePageChange()

      expect(mockFetcher).toHaveBeenCalledTimes(1)
    })
  })

  describe('初始化', () => {
    it('应使用 defaultSize 初始化 query.size', () => {
      mockFetcher = vi.fn()

      const { query } = useTable<TestQuery>(mockFetcher, {
        defaultSize: 20,
      })

      expect(query.size).toBe(20)
      expect(query.page).toBe(1)
    })

    it('defaultSize 默认为 10', () => {
      mockFetcher = vi.fn()

      const { query } = useTable<TestQuery>(mockFetcher)

      expect(query.size).toBe(10)
    })

    it('应使用 defaultQuery 初始化查询参数', () => {
      mockFetcher = vi.fn()

      const { query } = useTable<TestQuery>(mockFetcher, {
        defaultQuery: { keyword: 'initial', page: 1, size: 5 },
      })

      expect(query.keyword).toBe('initial')
      expect(query.page).toBe(1)
      expect(query.size).toBe(5)
    })
  })
})