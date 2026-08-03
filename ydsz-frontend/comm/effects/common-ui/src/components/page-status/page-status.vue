<script setup lang="ts">
/**
 * PageStatus — 统一页面状态容器
 *
 * 以声明式 status prop 切换 loading / error / empty / success 四种状态，
 * 消除 9 个子应用中各自实现的散落状态判断逻辑。
 *
 * @example
 * ```vue
 * <PageStatus status="loading" />
 * <PageStatus status="empty" description="暂无项目" @action="create" action-text="新建项目" />
 * <PageStatus status="error" message="加载失败" @retry="fetch" />
 * <PageStatus status="success">
 *   <DataTable :data="list" />
 * </PageStatus>
 * ```
 *
 * @path comm/effects/common-ui/src/components/page-status/page-status.vue
 * @author ydsz-team
 * @since 3.0.0
 */
defineOptions({ name: 'PageStatus' });

type Status = 'idle' | 'loading' | 'empty' | 'error' | 'success';

withDefaults(
  defineProps<{
    /** 当前状态 */
    status?: Status;
    /** 空状态描述 */
    description?: string;
    /** 空状态操作按钮文字 */
    actionText?: string;
    /** 错误消息 */
    message?: string;
    /** 加载文字 */
    loadingText?: string;
    /** 是否显示重试按钮（error 状态） */
    showRetry?: boolean;
    /** 是否显示返回按钮（error 状态） */
    showBack?: boolean;
  }>(),
  {
    status: 'idle',
    actionText: undefined,
    description: '暂无数据',
    loadingText: '加载中...',
    message: '数据加载失败，请稍后再试',
    showBack: false,
    showRetry: true,
  },
);

const emit = defineEmits<{
  /** error 状态点击重试 */
  retry: [];
  /** empty 状态点击操作按钮 */
  action: [];
}>();
</script>

<template>
  <div class="page-status" :data-status="status">
    <!-- loading -->
    <div v-if="status === 'loading'" class="status-panel">
      <div class="spinner" />
      <p class="status-text">{{ loadingText }}</p>
    </div>

    <!-- empty -->
    <div v-else-if="status === 'empty'" class="status-panel">
      <div class="empty-icon">
        <svg viewBox="0 0 80 80" width="80" height="80" fill="none">
          <rect x="12" y="16" width="56" height="48" rx="4" stroke="currentColor" stroke-width="2" opacity="0.15" />
          <line x1="20" y1="30" x2="48" y2="30" stroke="currentColor" stroke-width="2" stroke-linecap="round" opacity="0.1" />
          <line x1="20" y1="38" x2="60" y2="38" stroke="currentColor" stroke-width="2" stroke-linecap="round" opacity="0.1" />
          <line x1="20" y1="46" x2="40" y2="46" stroke="currentColor" stroke-width="2" stroke-linecap="round" opacity="0.1" />
        </svg>
      </div>
      <p class="status-text">{{ description }}</p>
      <el-button v-if="actionText" type="primary" @click="emit('action')">
        {{ actionText }}
      </el-button>
    </div>

    <!-- error -->
    <div v-else-if="status === 'error'" class="status-panel">
      <div class="error-icon">
        <svg viewBox="0 0 64 64" width="64" height="64" fill="none">
          <circle cx="32" cy="32" r="30" stroke="currentColor" stroke-width="2" opacity="0.2" />
          <path d="M32 20v16M32 44v2" stroke="currentColor" stroke-width="3" stroke-linecap="round" opacity="0.6" />
        </svg>
      </div>
      <p class="status-text">{{ message }}</p>
      <div class="status-actions">
        <el-button v-if="showRetry" type="primary" @click="emit('retry')">
          重新加载
        </el-button>
        <el-button v-if="showBack" @click="$router.back()">
          返回上一页
        </el-button>
      </div>
    </div>

    <!-- success / idle → 渲染默认插槽 -->
    <slot v-else />
  </div>
</template>

<style scoped>
.page-status {
  min-height: 160px;
}

.status-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
  min-height: inherit;
}

.status-text {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  margin: 16px 0 20px;
  max-width: 360px;
}

.status-actions {
  display: flex;
  gap: 12px;
}

.empty-icon,
.error-icon {
  color: var(--el-text-color-placeholder);
  margin-bottom: 4px;
}

.error-icon {
  color: var(--el-color-warning);
}

.spinner {
  width: 36px;
  height: 36px;
  border: 3px solid var(--el-border-color-lighter);
  border-top-color: var(--el-color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 4px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
