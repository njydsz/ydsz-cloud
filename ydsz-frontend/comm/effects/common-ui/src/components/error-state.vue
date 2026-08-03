<!--
  ErrorState — 统一错误状态展示组件

  用于 API 请求失败、网络异常等场景，提供友好提示 + 重试入口。

  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * ErrorState — 统一错误状态展示组件
 *
 * 用于 API 请求失败、网络异常等场景，提供友好提示 + 重试入口。
 *
 * @example
 * ```vue
 * <ErrorState @retry="fetchData" />
 * <ErrorState message="项目数据加载失败" show-back />
 * ```
 */
defineOptions({ name: 'ErrorState' });

withDefaults(
  defineProps<{
    message?: string;
    showBack?: boolean;
    showRetry?: boolean;
  }>(),
  {
    message: '数据加载失败，请稍后再试',
    showBack: false,
    showRetry: true,
  },
);

const emit = defineEmits<{
  retry: [];
}>();
</script>

<template>
  <div class="error-state">
    <div class="error-icon">
      <svg viewBox="0 0 64 64" width="64" height="64" fill="none">
        <circle cx="32" cy="32" r="30" stroke="currentColor" stroke-width="2" opacity="0.2" />
        <path d="M32 20v16M32 44v2" stroke="currentColor" stroke-width="3" stroke-linecap="round" opacity="0.6" />
      </svg>
    </div>
    <p class="error-message">{{ message }}</p>
    <div class="error-actions">
      <el-button v-if="showRetry" type="primary" @click="emit('retry')">
        重新加载
      </el-button>
      <el-button v-if="showBack" @click="$router.back()">
        返回上一页
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}
.error-icon {
  color: var(--el-color-warning);
  margin-bottom: 16px;
}
.error-message {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  margin: 0 0 20px;
  max-width: 360px;
}
.error-actions {
  display: flex;
  gap: 12px;
}
</style>
