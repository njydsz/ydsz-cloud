/**
 * @file useTable composable 单元测试
 * @description 测试通用分页查询 composable 的核心逻辑：
 *   - 初始化状态（loading/list/total/query）
 *   - fetchData 成功/失败/取消场景
 *   - handleQuery 重置 page
 *   - resetQuery 清空查询条件
 *   - persistSizeKey 持久化
 * @module composables/__tests__/useTable
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useTable, type UseTableQuery } from '../useTable'

// 定义测试用查询类型
interface TestQuery extends UseTableQuery {
  keyword?: string
  status?: string
}

// 定义测试用数据类型
interface TestData {
  id: number
  name: string
}

// Mock fetcher 函数
function createMockFetcher(data: TestData[] = [], total = 0) {
  return vi.fn().mockResolvedValue({
    data: {
      list: data,
      total,
    },
  })
}

describe('useTable', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('应该以正确的初始值初始化', () => {
    const fetcher = createMockFetcher()
    const { loading, list, total, query } = useTable<TestQuery>(fetcher as any)

    expect(loading.value).toBe(false)
    expect(list.value).toEqual([])
    expect(total.value).toBe(0)
    expect(query.page).toBe(1)
    expect(query.size).toBe(10)
  })

  it('应该使用 defaultSize 初始化 size', () => {
    const fetcher = createMockFetcher()
    const { query } = useTable<TestQuery>(fetcher as any, { defaultSize: 20 })

    expect(query.size).toBe(20)
  })

  it('应该使用 defaultQuery 初始化查询参数', () => {
    const fetcher = createMockFetcher()
    const { query } = useTable<TestQuery>(fetcher as any, {
      defaultQuery: { keyword: 'test', status: 'active' },
    })

    expect(query.keyword).toBe('test')
    expect(query.status).toBe('active')
  })

  it('fetchData 成功时应该更新 list 和 total', async () => {
    const mockData = [
      { id: 1, name: 'Project A' },
      { id: 2, name: 'Project B' },
    ]
    const fetcher = createMockFetcher(mockData, 100)
    const { fetchData, list, total, loading } = useTable<TestQuery>(fetcher as any)

    await fetchData()

    expect(fetcher).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 10 }))
    expect(list.value).toEqual(mockData)
    expect(total.value).toBe(100)
    expect(loading.value).toBe(false)
  })

  it('fetchData 失败时应该设置 error 并保留旧数据', async () => {
    const fetcher = vi.fn().mockRejectedValue(new Error('Network error'))
    const { fetchData, error, loading } = useTable<TestQuery>(fetcher as any)

    await fetchData()

    expect(error.value).toBe('Network error')
    expect(loading.value).toBe(false)
  })

  it('fetchData 请求被取消时应该静默处理', async () => {
    const cancelError = new Error('canceled')
    cancelError.name = 'CanceledError'
    ;(cancelError as any).code = 'ERR_CANCELED'
    const fetcher = vi.fn().mockRejectedValue(cancelError)
    const { fetchData, error, loading } = useTable<TestQuery>(fetcher as any)

    await fetchData()

    expect(error.value).toBeNull()
    expect(loading.value).toBe(false)
  })

  it('handleQuery 应该重置 page 为 1', async () => {
    const fetcher = createMockFetcher([], 0)
    const { handleQuery, query } = useTable<TestQuery>(fetcher as any)

    query.page = 5
    await handleQuery()

    expect(query.page).toBe(1)
    expect(fetcher).toHaveBeenCalled()
  })

  it('resetQuery 应该清空查询条件并重置 page', async () => {
    const fetcher = createMockFetcher([], 0)
    const { resetQuery, query } = useTable<TestQuery>(fetcher as any, {
      defaultQuery: { keyword: 'default', status: 'active' },
    })

    query.keyword = 'changed'
    query.page = 5
    await resetQuery({ status: 'active' })

    expect(query.keyword).toBeUndefined()
    expect(query.status).toBe('active')
    expect(query.page).toBe(1)
  })

  it('persistSizeKey 应该持久化 size 到 localStorage', async () => {
    const fetcher = createMockFetcher([], 0)
    const { query } = useTable<TestQuery>(fetcher as any, {
      persistSizeKey: 'test-list',
    })

    query.size = 50
    // 等待 watch 触发
    await vi.waitFor(() => {
      expect(localStorage.getItem('pmis-pageSize-test-list')).toBe('50')
    })
  })

  it('persistSizeKey 应该从 localStorage 恢复 size', () => {
    localStorage.setItem('pmis-pageSize-test-list', '25')
    const fetcher = createMockFetcher([], 0)
    const { query } = useTable<TestQuery>(fetcher as any, {
      persistSizeKey: 'test-list',
    })

    expect(query.size).toBe(25)
  })

  it('兼容直接返回 PageResult（无 data 包装）的 fetcher', async () => {
    const mockData = [{ id: 1, name: 'Test' }]
    const fetcher = vi.fn().mockResolvedValue({
      list: mockData,
      total: 1,
    })
    const { fetchData, list, total } = useTable<TestQuery>(fetcher as any)

    await fetchData()

    expect(list.value).toEqual(mockData)
    expect(total.value).toBe(1)
  })
})
