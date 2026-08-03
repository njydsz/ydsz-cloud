<script setup lang="ts">
/**
 * EmptyState — 统一空状态展示组件
 *
 * 用于列表无数据、搜索结果为空等场景。
 *
 * @example
 * ```vue
 * <EmptyState description="暂无项目数据" />
 * <EmptyState description="未找到匹配结果">
 *   <el-button type="primary">新建项目</el-button>
 * </EmptyState>
 * ```
 */
defineOptions({ name: 'EmptyState' });

withDefaults(
  defineProps<{
    description?: string;
    image?: string;
  }>(),
  {
    description: '暂无数据',
  },
);
</script>

<template>
  <div class="empty-state">
    <div class="empty-icon">
      <svg viewBox="0 0 80 80" width="80" height="80" fill="none">
        <rect x="12" y="16" width="56" height="48" rx="4" stroke="currentColor" stroke-width="2" opacity="0.15" />
        <line x1="20" y1="30" x2="48" y2="30" stroke="currentColor" stroke-width="2" stroke-linecap="round" opacity="0.1" />
        <line x1="20" y1="38" x2="60" y2="38" stroke="currentColor" stroke-width="2" stroke-linecap="round" opacity="0.1" />
        <line x1="20" y1="46" x2="40" y2="46" stroke="currentColor" stroke-width="2" stroke-linecap="round" opacity="0.1" />
      </svg>
    </div>
    <p class="empty-description">{{ description }}</p>
    <div v-if="$slots.default" class="empty-actions">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}
.empty-icon {
  color: var(--el-text-color-placeholder);
  margin-bottom: 16px;
}
.empty-description {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  margin: 0 0 20px;
}
.empty-actions {
  display: flex;
  gap: 12px;
}
</style>
