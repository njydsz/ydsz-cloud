<!--
 * virtual-list 通用组件 — 通用虚拟列表
 *
 * @path comm\effects\shared-business\src\components\virtual-list.vue
 * @author ydsz-team
 * @since 1.1.0
-->
<script lang="ts" setup>
/**
 * 通用虚拟列表 — 基于 @tanstack/vue-virtual
 *
 * 适用于任意大数据量列表渲染场景（非表格、非下拉）：
 * 如消息列表、日志流、审计记录、树形节点等。
 */
import { ref } from 'vue';

import { useVirtualizer } from '@tanstack/vue-virtual';

interface Props {
  /** 列表数据 */
  items: any[];
  /** 行高（px），默认 36 */
  rowHeight?: number;
  /** 容器预估高度（px），默认 400 */
  height?: number;
  /** 上下缓冲区行数，默认 5 */
  overscan?: number;
}

const props = withDefaults(defineProps<Props>(), {
  rowHeight: 36,
  height: 400,
  overscan: 5,
});

const parentRef = ref<HTMLElement>();

const virtualizer = useVirtualizer({
  count: props.items.length,
  getScrollElement: () => parentRef.value,
  estimateSize: () => props.rowHeight,
  overscan: props.overscan,
});
</script>

<template>
  <div
    ref="parentRef"
    class="virtual-list"
    :style="{ height: `${height}px`, overflowY: 'auto' }"
  >
    <div
      :style="{
        height: `${virtualizer.getTotalSize()}px`,
        width: '100%',
        position: 'relative',
      }"
    >
      <div
        v-for="item in virtualizer.getVirtualItems()"
        :key="item.key"
        :style="{
          position: 'absolute',
          top: '0',
          left: '0',
          width: '100%',
          height: `${item.size}px`,
          transform: `translateY(${item.start}px)`,
        }"
      >
        <slot name="item" :item="items[item.index]" :index="item.index" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.virtual-list {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 4px 0;
}
</style>
