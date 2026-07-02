<!--
  @file 通用表格骨架屏组件
  @description 列表首次加载时以骨架占位替代空白表格，缓解感知加载延迟
  @module components/common/SkeletonTable
-->
<script setup lang="ts">
/**
 * 表格骨架屏
 *
 * 在列表首次加载、数据尚未返回时，渲染与表格结构一致的骨架占位，
 * 避免出现「空白表格闪烁 → 数据填充」的跳变，提升首屏体验。
 *
 * 使用示例：
 *   <SkeletonTable :rows="8" :columns="6" />
 */
withDefaults(
  defineProps<{
    /** 骨架行数 */
    rows?: number
    /** 骨架列数 */
    columns?: number
  }>(),
  {
    rows: 5,
    columns: 6,
  },
)
</script>

<template>
  <div class="skeleton-table">
    <div class="skeleton-header">
      <el-skeleton-item v-for="c in columns" :key="c" variant="text" style="width: 100%; height: 32px" />
    </div>
    <div v-for="r in rows" :key="r" class="skeleton-row">
      <el-skeleton-item v-for="c in columns" :key="c" variant="text" style="width: 100%; height: 28px" />
    </div>
  </div>
</template>

<style scoped>
.skeleton-table {
  padding: 0;
}
.skeleton-header {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.skeleton-header :deep(.el-skeleton__item) {
  flex: 1;
}
.skeleton-row {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.skeleton-row :deep(.el-skeleton__item) {
  flex: 1;
}
</style>
