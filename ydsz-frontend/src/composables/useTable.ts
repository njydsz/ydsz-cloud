/**
 * @file 通用分页查询 composable
 * @description 封装列表页的分页、加载、查询参数等通用逻辑，配合 PageLayout 组件使用，大幅减少页面样板代码
 * @module composables/useTable
 *
 * 用法：
 * ```ts
 * const { loading, list, total, query, fetchData, handleQuery, resetQuery } =
 *   useTable(pageApi, { defaultSize: 20, persistSizeKey: 'project-list' })
 * ```
 */
import { ref, reactive, watch, onUnmounted, getCurrentInstance, type Ref } from 'vue'
import { requestCanceler } from '@/utils/request-canceler'

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
  /**
   * 持久化每页条数的 localStorage key（建议传路由名以保证每页独立）
   * - 不传则不持久化
   * - 传入后：初始化时从 localStorage 恢复用户上次选择，size 变化时回写
   */
  persistSizeKey?: string
  /**
   * 组件卸载时需要取消的请求 URL 片段（模糊匹配）
   *
   * 传入后，组件 onUnmounted 时会调用 requestCanceler.cancelByUrl(cancelUrl)，
   * 取消当前表格未完成的查询请求，避免数据竞态与内存泄漏。
   * 不传则不执行取消（适合全局共享的表格或无需取消的场景）。
   */
  cancelUrl?: string
}

/** localStorage 中 pageSize 持久化的统一前缀，便于清理与避免冲突 */
const PAGE_SIZE_KEY_PREFIX = 'pmis-pageSize-'

/** 读取持久化的每页条数 */
function loadPersistedSize(key?: string): number | null {
  if (!key) return null
  try {
    const v = localStorage.getItem(PAGE_SIZE_KEY_PREFIX + key)
    if (!v) return null
    const n = Number(v)
    return Number.isFinite(n) && n > 0 ? n : null
  } catch {
    return null
  }
}

/** 写入持久化的每页条数 */
function savePersistedSize(key: string | undefined, size: number): void {
  if (!key) return
  try {
    localStorage.setItem(PAGE_SIZE_KEY_PREFIX + key, String(size))
  } catch {
    /* localStorage 可能不可用 */
  }
}

/**
 * 通用分页查询 composable
 *
 * @param fetcher - 数据拉取函数，接收 query 返回 PageResult
 * @param options - 初始化选项（默认分页大小、初始查询参数、持久化 key）
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
  /** 最近一次请求的错误信息（null 表示无错误） */
  const error = ref<string | null>(null)

  // 组件卸载时取消未完成的查询请求，避免数据竞态与内存泄漏
  // 仅在组件上下文中注册（测试 / 非组件场景下 getCurrentInstance 返回 null，跳过避免告警）
  if (getCurrentInstance() && options.cancelUrl) {
    onUnmounted(() => {
      requestCanceler.cancelByUrl(options.cancelUrl!)
    })
  }

  // 解析初始 size：优先使用持久化值，其次 defaultQuery.size，最后 defaultSize 或 10
  const persistedSize = loadPersistedSize(options.persistSizeKey)
  const initialSize =
    (persistedSize as Q['size'] | null) ??
    (options.defaultQuery?.size as Q['size'] | undefined) ??
    (options.defaultSize as Q['size'] | undefined) ??
    (10 as Q['size'])

  /** 查询参数（响应式，绑定到搜索表单） */
  const query = reactive<Q>({
    ...(options.defaultQuery as Q),
    page: (options.defaultQuery?.page ?? 1) as Q['page'],
    size: initialSize,
  } as unknown as Q)

  // P1: 持久化每页条数（用户切换 pageSize 后，下次访问恢复）
  if (options.persistSizeKey) {
    watch(
      () => query.size,
      (newSize) => savePersistedSize(options.persistSizeKey, newSize),
    )
  }

  /** 拉取数据（兼容 ApiResponse<PageResult> 与 PageResult 两种返回结构） */
  async function fetchData(): Promise<void> {
    loading.value = true
    // 进入新一轮请求前清空历史错误
    error.value = null
    try {
      const res = await fetcher(query as unknown as Q) as {
        data?: { list?: unknown[]; total?: number }
        list?: unknown[]
        total?: number
      }
      list.value = res?.data?.list ?? res?.list ?? []
      total.value = res?.data?.total ?? res?.total ?? 0
    } catch (e: unknown) {
      // 请求被主动取消（组件卸载 / 路由切换 / 重复请求）：静默处理，不清空列表、不弹错
      // CanceledError 的 name 为 'CanceledError'，code 为 'ERR_CANCELED'
      if (e instanceof Error && (e.name === 'CanceledError' || (e as Error & { code?: string }).code === 'ERR_CANCELED')) {
        // 还原 loading，保留已有列表数据，不抛错
        return
      }
      // 请求失败：记录错误状态（由 PageLayout 展示 network preset + 重试按钮）
      // 不清空已有列表数据：翻页失败时保留上一次数据，避免表格闪烁
      const msg = e instanceof Error ? e.message : String(e)
      error.value = msg
      // 错误提示由全局 request 拦截器统一处理（ElMessage.error），此处不重复弹窗
      console.error('[useTable] fetchData failed:', e)
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

  // ==================== 乐观更新（Optimistic UI） ====================
  // 先更新本地列表，再发请求；失败时回滚，让用户感知"即时响应"
  // 适用于删除、状态变更等高频操作，大幅减少等待感

  /** 乐观更新前的快照（用于回滚） */
  let snapshot: unknown[] = []

  /** 保存当前列表快照（乐观操作前调用） */
  function saveSnapshot(): void {
    snapshot = [...list.value]
  }

  /** 回滚到快照 */
  function rollback(): void {
    list.value = snapshot
  }

  /**
   * 乐观删除：先从列表移除，再调 API；失败时回滚
   *
   * @param ids 要删除的记录 ID 数组
   * @param apiCall 删除 API 调用函数
   * @param idField ID 字段名，默认 'id'
   * @returns API 是否成功
   */
  async function optimisticDelete(
    ids: (string | number)[],
    apiCall: () => Promise<unknown>,
    idField = 'id',
  ): Promise<boolean> {
    saveSnapshot()
    // 立即从列表中移除
    list.value = list.value.filter(
      (item) => !ids.includes((item as Record<string, unknown>)[idField] as string | number),
    )
    // 同步减少 total（失败时回滚会恢复）
    total.value = Math.max(0, total.value - ids.length)

    try {
      await apiCall()
      return true
    } catch (e) {
      // 回滚列表和 total
      rollback()
      total.value = total.value + ids.length
      throw e
    }
  }

  /**
   * 乐观更新：先更新列表中的记录，再调 API；失败时回滚
   *
   * @param id 要更新的记录 ID
   * @param changes 要更新的字段
   * @param apiCall 更新 API 调用函数
   * @param idField ID 字段名，默认 'id'
   * @returns API 是否成功
   */
  async function optimisticUpdate(
    id: string | number,
    changes: Record<string, unknown>,
    apiCall: () => Promise<unknown>,
    idField = 'id',
  ): Promise<boolean> {
    saveSnapshot()
    // 立即更新列表中的记录
    list.value = list.value.map((item) => {
      const record = item as Record<string, unknown>
      if (record[idField] === id) {
        return { ...record, ...changes }
      }
      return item
    })

    try {
      await apiCall()
      return true
    } catch (e) {
      rollback()
      throw e
    }
  }

  /**
   * 乐观批量更新状态：先更新列表中匹配记录的状态，再调 API；失败时回滚
   *
   * @param ids 要更新的记录 ID 数组
   * @param changes 要更新的字段
   * @param apiCall 批量更新 API 调用函数
   * @param idField ID 字段名，默认 'id'
   * @returns API 是否成功
   */
  async function optimisticBatchUpdate(
    ids: (string | number)[],
    changes: Record<string, unknown>,
    apiCall: () => Promise<unknown>,
    idField = 'id',
  ): Promise<boolean> {
    saveSnapshot()
    const idSet = new Set(ids)
    list.value = list.value.map((item) => {
      const record = item as Record<string, unknown>
      if (idSet.has(record[idField] as string | number)) {
        return { ...record, ...changes }
      }
      return item
    })

    try {
      await apiCall()
      return true
    } catch (e) {
      rollback()
      throw e
    }
  }

  return {
    loading,
    list,
    total,
    error,
    query,
    fetchData,
    handleQuery,
    resetQuery,
    handlePageChange,
    // 乐观更新方法
    optimisticDelete,
    optimisticUpdate,
    optimisticBatchUpdate,
  }
}
