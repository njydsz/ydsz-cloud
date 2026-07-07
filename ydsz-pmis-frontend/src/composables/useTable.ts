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
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
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
  const { t } = useI18n()
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
      // 请求失败：清空列表数据，避免用户误以为是空数据
      list.value = []
      total.value = 0
      const msg = e instanceof Error ? e.message : String(e)
      error.value = msg
      ElMessage.error(`${t('common.msg_fetch_failed')}: ${msg}`)
      // 不吞掉错误，便于上层（如调用方）感知失败
      console.error('[useTable] fetchData failed:', e)
      throw e
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
    error,
    query,
    fetchData,
    handleQuery,
    resetQuery,
    handlePageChange,
  }
}
