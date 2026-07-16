/**
 * @file 分片渲染 composable
 * @description 将大数组分片渐进式渲染，避免一次性创建大量 DOM 导致主线程阻塞。
 *
 * 背景：
 *   虚拟滚动适合等高列表，但某些场景（如树形数据、不等高卡片列表）
 *   不适合用虚拟滚动。此时可使用分片渲染：先渲染前 N 条，然后通过
 *   requestAnimationFrame 逐批追加，保证首屏快速可交互。
 *
 * 使用方式：
 *   ```ts
 *   const { renderList, isLoading, loadMore, hasMore } = useChunkedRender(
 *     allData,
 *     { initialCount: 50, chunkSize: 30 }
 *   )
 *   ```
 *
 *   模板：
 *   ```vue
 *   <div v-for="item in renderList" :key="item.id">{{ item.name }}</div>
 *   <el-button v-if="hasMore" @click="loadMore">加载更多</el-button>
 *   ```
 */
import { ref, computed, watch, onMounted, type Ref } from 'vue'

/** useChunkedRender 配置项 */
export interface UseChunkedRenderOptions {
  /** 初始渲染数量，默认 50 */
  initialCount?: number
  /** 每次追加数量，默认 30 */
  chunkSize?: number
  /** 是否自动加载（通过 IntersectionObserver），默认 false */
  autoLoad?: boolean
  /** 自动加载时的根 margin，默认 '100px' */
  rootMargin?: string
  /** 最大渲染数量（防止内存溢出），默认 5000 */
  maxRender?: number
}

/** useChunkedRender 返回值 */
export interface UseChunkedRenderReturn<T> {
  /** 当前已渲染的数据 */
  renderList: Ref<T[]>
  /** 是否还有更多数据未渲染 */
  hasMore: Ref<boolean>
  /** 是否正在加载下一批 */
  isLoading: Ref<boolean>
  /** 手动加载更多 */
  loadMore: () => void
  /** 重置为初始数量 */
  reset: () => void
  /** 用于 IntersectionObserver 的 sentinel ref */
  sentinelRef: Ref<HTMLElement | null>
}

/**
 * 分片渲染 composable
 *
 * @param sourceData 源数据 ref
 * @param options 配置项
 * @returns 分片渲染控制对象
 */
export function useChunkedRender<T>(
  sourceData: Ref<T[]>,
  options: UseChunkedRenderOptions = {},
): UseChunkedRenderReturn<T> {
  const {
    initialCount = 50,
    chunkSize = 30,
    autoLoad = false,
    rootMargin = '100px',
    maxRender = 5000,
  } = options

  /** 当前已渲染数量 */
  const renderedCount = ref(0)
  /** 是否正在加载 */
  const isLoading = ref(false)
  /** sentinel 元素 ref */
  const sentinelRef = ref<HTMLElement | null>(null)
  /** IntersectionObserver 实例 */
  let observer: IntersectionObserver | null = null

  /** 当前已渲染的数据 */
  const renderList = computed<T[]>(() => {
    const max = Math.min(renderedCount.value, sourceData.value.length, maxRender)
    return sourceData.value.slice(0, max)
  })

  /** 是否还有更多 */
  const hasMore = computed(
    () => renderedCount.value < sourceData.value.length && renderedCount.value < maxRender,
  )

  /** 加载更多 */
  function loadMore(): void {
    if (isLoading.value || !hasMore.value) return
    isLoading.value = true

    // 使用 requestAnimationFrame 确保不阻塞主线程
    requestAnimationFrame(() => {
      renderedCount.value = Math.min(
        renderedCount.value + chunkSize,
        sourceData.value.length,
        maxRender,
      )
      isLoading.value = false
    })
  }

  /** 重置 */
  function reset(): void {
    renderedCount.value = Math.min(initialCount, sourceData.value.length)
  }

  // 源数据变化时重置
  watch(
    () => sourceData.value,
    () => {
      reset()
    },
    { immediate: true },
  )

  // 设置 IntersectionObserver 自动加载
  function setupObserver(): void {
    if (!autoLoad || !sentinelRef.value || typeof IntersectionObserver === 'undefined') return

    if (observer) {
      observer.disconnect()
    }

    observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting && hasMore.value) {
          loadMore()
        }
      },
      { rootMargin },
    )

    observer.observe(sentinelRef.value)
  }

  // 监听 sentinel ref 变化
  watch(sentinelRef, (el) => {
    if (el && autoLoad) {
      setupObserver()
    } else if (observer) {
      observer.disconnect()
      observer = null
    }
  })

  // 组件卸载时清理
  // 注：onUnmounted 会在组件卸载时调用，但在 composable 外部使用时需注意
  if (typeof window !== 'undefined') {
    // 使用 onScopeDispose 确保在 setup 作用域中正确清理
    import('vue').then(({ onScopeDispose }) => {
      onScopeDispose(() => {
        if (observer) {
          observer.disconnect()
          observer = null
        }
      })
    })
  }

  // 初始加载
  onMounted(() => {
    if (autoLoad && sentinelRef.value) {
      setupObserver()
    }
  })

  return {
    renderList,
    hasMore,
    isLoading,
    loadMore,
    reset,
    sentinelRef,
  }
}
