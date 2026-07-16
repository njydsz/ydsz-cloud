<template>
  <div class="skeleton-page" role="status" aria-live="polite">
    <!-- 页面级骨架屏：模拟典型列表页布局 -->
    <div class="skeleton-header">
      <el-skeleton-item variant="text" class="skeleton-title" />
      <div class="skeleton-actions">
        <el-skeleton-item variant="text" class="skeleton-btn" />
        <el-skeleton-item variant="text" class="skeleton-btn" />
      </div>
    </div>
    <div class="skeleton-filters">
      <el-skeleton-item variant="rect" class="skeleton-input" />
      <el-skeleton-item variant="rect" class="skeleton-input" />
      <el-skeleton-item variant="rect" class="skeleton-input" />
      <el-skeleton-item variant="text" class="skeleton-search-btn" />
    </div>
    <div class="skeleton-table">
      <div v-for="i in rows" :key="i" class="skeleton-row">
        <el-skeleton-item variant="text" class="skeleton-cell" />
        <el-skeleton-item variant="text" class="skeleton-cell" />
        <el-skeleton-item variant="text" class="skeleton-cell" />
        <el-skeleton-item variant="text" class="skeleton-cell" />
        <el-skeleton-item variant="text" class="skeleton-cell-short" />
      </div>
    </div>
    <div class="skeleton-pagination">
      <el-skeleton-item variant="text" class="skeleton-page-num" />
      <el-skeleton-item variant="text" class="skeleton-page-num" />
      <el-skeleton-item variant="text" class="skeleton-page-num" />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 页面级骨架屏组件（P2-4）
 *
 * <p>在数据加载期间展示骨架屏，消除白屏闪烁，提升感知性能。
 * 模拟典型列表页布局：标题栏 + 筛选区 + 表格 + 分页。
 *
 * <p>使用方式：
 * ```vue
 * <template>
 *   <PageSkeleton v-if="loading" />
 *   <ActualContent v-else />
 * </template>
 * ```
 *
 * @author ydsz-team
 * @since 1.0.0
 */

interface Props {
  /** 骨架表格行数 */
  rows?: number
}

withDefaults(defineProps<Props>(), {
  rows: 8,
})
</script>

<style scoped lang="scss">
.skeleton-page {
  padding: 16px 24px;

  .skeleton-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    .skeleton-title {
      width: 200px;
      height: 28px;
    }

    .skeleton-actions {
      display: flex;
      gap: 12px;

      .skeleton-btn {
        width: 80px;
        height: 32px;
      }
    }
  }

  .skeleton-filters {
    display: flex;
    gap: 12px;
    margin-bottom: 20px;

    .skeleton-input {
      width: 180px;
      height: 32px;
    }

    .skeleton-search-btn {
      width: 60px;
      height: 32px;
    }
  }

  .skeleton-table {
    display: flex;
    flex-direction: column;
    gap: 12px;

    .skeleton-row {
      display: flex;
      gap: 16px;
      align-items: center;

      .skeleton-cell {
        flex: 1;
        height: 20px;
      }

      .skeleton-cell-short {
        width: 80px;
        height: 20px;
      }
    }
  }

  .skeleton-pagination {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 20px;

    .skeleton-page-num {
      width: 32px;
      height: 32px;
    }
  }
}
</style>
