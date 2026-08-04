<!--
 * async-state 通用容器组件
 *
 * @path comm\effects\shared-business\src\components\async-state.vue
 * @author ydsz-team
 * @since 1.1.0
-->
<script lang="ts" setup>
/**
 * 异步状态容器 — 根据 loading/error/empty/data 自动切换展示
 *
 * 用法：
 * ```vue
 * <AsyncState :loading="loading" :error="error" :empty="list.length === 0">
 *   <!-- 数据就绪后的默认插槽 -->
 *   <el-table :data="list" />
 * </AsyncState>
 * ```
 */
import { ElSkeleton, ElSkeletonItem } from 'element-plus';

interface Props {
  /** 是否加载中 */
  loading?: boolean;
  /** 错误信息（非空即错误态） */
  error?: string | Error | null;
  /** 是否空数据 */
  empty?: boolean;
  /** 空状态文案 */
  emptyText?: string;
  /** 错误标题 */
  errorTitle?: string;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  error: null,
  empty: false,
  emptyText: '暂无数据',
  errorTitle: '加载失败',
});

defineEmits<{
  retry: [];
}>();
</script>

<template>
  <!-- 加载中 -->
  <el-skeleton v-if="loading" animated :rows="6" />

  <!-- 错误态 -->
  <div v-else-if="error" class="async-state">
    <div class="async-state__error">
      <div class="async-state__icon">!</div>
      <p>{{ errorTitle }}</p>
      <p v-if="typeof error === 'string'" class="async-state__msg">{{ error }}</p>
      <el-button type="primary" size="small" @click="$emit('retry')">重试</el-button>
    </div>
  </div>

  <!-- 空态 -->
  <div v-else-if="empty" class="async-state">
    <div class="async-state__empty">
      <p>{{ emptyText }}</p>
      <slot name="empty" />
    </div>
  </div>

  <!-- 数据态 -->
  <slot v-else />
</template>

<style scoped>
.async-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}
.async-state__error,
.async-state__empty {
  text-align: center;
  color: #909399;
}
.async-state__icon {
  width: 48px;
  height: 48px;
  margin: 0 auto 12px;
  border-radius: 50%;
  background: #fef0f0;
  color: #f56c6c;
  font-size: 26px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.async-state__msg {
  font-size: 12px;
  margin: 4px 0 12px;
}
.async-state__empty {
  padding: 24px 0;
}
</style>
