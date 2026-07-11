/**
 * @file 虚拟列表 composable
 * @description 实现通用的虚拟滚动（windowing）能力，仅渲染可视区域内的列表项。
 *
 * 背景：
 *   当列表数据量超过 500 条时，一次性渲染所有 DOM 节点会导致明显卡顿。
 *   虚拟滚动通过只渲染可视区域 + 上下缓冲区的项，将 DOM 节点数控制在常数级。
 *
 * 使用方式：
 *   ```ts
 *   const { visibleData, containerStyle, onScroll, scrollToIndex } = useVirtualList(
 *     dataRef,
 *     { itemHeight: 48, overscan: 5 }
 *   )
 *   ```
 *
 *   模板：
 *   ```vue
 *   <div class="scroll-container" :style="containerStyle" @scroll="onScroll">
 *     <div v-for="item in visibleData" :key="item.id" :style="{ transform: `translateY(${item._offset}px)` }">
 *       {{ item.name }}
 *     </div>
 *   </div>
 *   ```
 */
import { ref, computed, type Ref, watch } from 'vue'

/** useVirtualList 配置项 */
export interface UseVirtualListOptions<T> {
  /** 每项高度（px）。如果是动态高度，传一个估算值，滚动时会有偏差但功能正常 */
  itemHeight: number
  /** 可视区域外上下额外渲染的项数（缓冲区），默认 5 */
  overscan?: number
  /** 可视区域高度（px）。不传时默认 400 */
  viewportHeight?: number
  /** 获取项的唯一 key（用于 stable rendering） */
  getKey?: (item: T, index: number) => string | number
}

/** 虚拟列表可视区域项（带位置信息） */
export interface VirtualListItem<T> {
  /** 原始数据 */
  data: T
  /** 在原始列表中的索引 */
  index: number
  /** 距顶部的偏移量（px） */
  _offset: number
  /** 唯一 key */
  _key: string | number
}

/** useVirtualList 返回值 */
export interface UseVirtualListReturn<T> {
  /** 当前可视区域的项（含位置信息） */
  visibleData: Ref<VirtualListItem<T>[]>
  /** 容器内部占位 div 的总高度（用于撑开滚动条） */
  innerHeight: Ref<number>
  /** 滚动容器的 padding-top（用于偏移可视区域项） */
  paddingTop: Ref<number>
  /** 滚动事件处理器 */
  onScroll: (e: Event) => void
  /** 滚动到指定索引 */
  scrollToIndex: (index: number) => void
  /** 当前可视区域起始索引 */
  startIndex: Ref<number>
  /** 当前可视区域结束索引 */
  endIndex: Ref<number>
}

/**
 * 虚拟列表 composable
 *
 * @param data 列表数据 ref
 * @param options 配置项
 * @returns 虚拟列表控制对象
 */
export function useVirtualList<T>(
  data: Ref<T[]>,
  options: UseVirtualListOptions<T>,
): UseVirtualListReturn<T> {
  const { itemHeight, overscan = 5, viewportHeight = 400 } = options

  /** 当前滚动位置 */
  const scrollTop = ref(0)

  /** 总高度 */
  const innerHeight = computed(() => data.value.length * itemHeight)

  /** 可视区域起始索引 */
  const startIndex = computed(() => {
    const start = Math.floor(scrollTop.value / itemHeight)
    return Math.max(0, start - overscan)
  })

  /** 可视区域结束索引 */
  const endIndex = computed(() => {
    const visibleCount = Math.ceil(viewportHeight / itemHeight)
    const end = startIndex.value + visibleCount + overscan * 2
    return Math.min(data.value.length, end)
  })

  /** padding-top（跳过未渲染的项） */
  const paddingTop = computed(() => startIndex.value * itemHeight)

  /** 可视区域数据 */
  const visibleData = computed<VirtualListItem<T>[]>(() => {
    const result: VirtualListItem<T>[] = []
    for (let i = startIndex.value; i < endIndex.value; i++) {
      const item = data.value[i]
      if (item == null) continue
      result.push({
        data: item,
        index: i,
        _offset: i * itemHeight,
        _key: options.getKey ? options.getKey(item, i) : i,
      })
    }
    return result
  })

  /** 滚动事件处理器 */
  function onScroll(e: Event): void {
    const target = e.target as HTMLElement
    scrollTop.value = target.scrollTop
  }

  /** 滚动到指定索引 */
  function scrollToIndex(index: number): void {
    const clampedIndex = Math.max(0, Math.min(data.value.length - 1, index))
    scrollTop.value = clampedIndex * itemHeight
  }

  // 数据变化时重置滚动位置
  watch(
    () => data.value.length,
    () => {
      scrollTop.value = 0
    },
  )

  return {
    visibleData,
    innerHeight,
    paddingTop,
    onScroll,
    scrollToIndex,
    startIndex,
    endIndex,
  }
}
