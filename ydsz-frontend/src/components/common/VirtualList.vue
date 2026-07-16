<!--
  @fileoverview VirtualList 虚拟列表组件
  @description 通用虚拟滚动列表组件，仅渲染可视区域内的项，适用于大数据量列表场景。
  基于 useVirtualList composable 实现。

  使用方式：
  ```vue
  <VirtualList :items="list" :item-height="48" :viewport-height="400">
    <template #default="{ item, index }">
      <div class="my-item">{{ item.name }}</div>
    </template>
  </VirtualList>
  ```

  @module components/common/VirtualList
  @since 2.1.0
-->
<script setup lang="ts" generic="T extends Record<string, unknown> = Record<string, unknown>">
/**
 * VirtualList - 虚拟滚动列表组件
 *
 * 仅渲染可视区域 + 缓冲区的列表项，适用于 500+ 条数据的列表场景。
 */
import { ref, computed } from 'vue'
import { useVirtualList, type VirtualListItem } from '@/composables/useVirtualList'

const props = withDefaults(
  defineProps<{
    /** 列表数据 */
    items: T[]
    /** 每项高度（px） */
    itemHeight: number
    /** 可视区域高度（px），默认 400 */
    viewportHeight?: number
    /** 缓冲区项数，默认 5 */
    overscan?: number
    /** 获取项的唯一 key */
    getKey?: (item: T, index: number) => string | number
    /** 自定义容器类名 */
    containerClass?: string
  }>(),
  {
    viewportHeight: 400,
    overscan: 5,
    containerClass: '',
  },
)

const itemsRef = computed(() => props.items)

const { visibleData, innerHeight, paddingTop, onScroll, scrollToIndex } = useVirtualList(itemsRef, {
  itemHeight: props.itemHeight,
  overscan: props.overscan,
  viewportHeight: props.viewportHeight,
  getKey: props.getKey,
})

const containerRef = ref<HTMLElement | null>(null)

defineExpose({
  /** 滚动到指定索引 */
  scrollToIndex,
  /** 滚动到顶部 */
  scrollToTop: () => scrollToIndex(0),
  /** 容器 ref */
  containerRef,
})
</script>

<template>
  <div
    ref="containerRef"
    class="virtual-list"
    :class="containerClass"
    :style="{ height: `${viewportHeight}px`, overflowY: 'auto', position: 'relative' }"
    @scroll="onScroll"
  >
    <!-- 占位区域（撑开滚动条） -->
    <div :style="{ height: `${innerHeight}px`, paddingTop: `${paddingTop}px` }">
      <!-- 可视区域项 -->
      <div
        v-for="item in visibleData"
        :key="item._key"
        class="virtual-list__item"
        :style="{
          height: `${itemHeight}px`,
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          transform: `translateY(${item._offset}px)`,
        }"
      >
        <slot :item="(item as VirtualListItem<T>).data" :index="item.index" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.virtual-list {
  will-change: scroll-position;
  -webkit-overflow-scrolling: touch;
}

.virtual-list__item {
  box-sizing: border-box;
}
</style>
