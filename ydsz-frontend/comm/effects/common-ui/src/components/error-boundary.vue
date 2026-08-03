<!--
 * 全局错误边界组件 — 捕获子组件树中的未处理错误，提供友好的降级 UI
 *
 * 对标 React Error Boundary 模式，在 Vue 3 中通过 onErrorCaptured 实现。
 * 用于包裹关键业务组件，防止局部错误导致整个应用崩溃。
 *
 * @example
 * ```vue
 * <ErrorBoundary>
 *   <CriticalComponent />
 * </ErrorBoundary>
 * ```
 *
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { ErrorInfo } from 'vue';

import { computed, onErrorCaptured, ref } from 'vue';

import { reportError } from '@ydsz/monitor';

import { ElButton, ElResult } from 'element-plus';

defineOptions({ name: 'ErrorBoundary' });

const props = withDefaults(
  defineProps<{
    /** 错误时展示的自定义消息 */
    errorMessage?: string;
    /** 是否展示重试按钮 */
    showRetry?: boolean;
    /** 是否展示返回按钮 */
    showBack?: boolean;
    /** 错误类型图标：'error' | 'warning' | 'info' */
    status?: 'error' | 'info' | 'warning';
    /** 是否展示错误详情（生产环境建议关闭） */
    showDetails?: boolean;
  }>(),
  {
    errorMessage: '页面加载出错，请稍后重试',
    showBack: false,
    showRetry: true,
    showDetails: false,
    status: 'error',
  },
);

const emit = defineEmits<{
  error: [error: Error, info: ErrorInfo];
  retry: [];
}>();

const hasError = ref(false);
const errorMessage = ref('');
const errorStack = ref<null | string>(null);

/** 是否展示错误状态 */
const showError = computed(() => hasError.value);

/**
 * 捕获子组件错误
 *
 * onErrorCaptured 会捕获所有子组件（包括嵌套层级）抛出的错误。
 * 返回 false 阻止错误继续向上传播，避免触发全局 errorHandler。
 */
onErrorCaptured((err: Error, _instance, info: ErrorInfo) => {
  hasError.value = true;
  errorMessage.value = err.message || props.errorMessage;
  errorStack.value = err.stack || null;

  // 上报错误到监控系统
  reportError('vue', err.message, {
    lifecycleHook: info,
    stack: err.stack,
  });

  // 触发自定义事件，允许父组件处理
  emit('error', err, info);

  // 开发环境打印详细错误
  if (!import.meta.env.PROD) {
    console.error('[ErrorBoundary] Captured error:', err, info);
  }

  // 返回 false 阻止错误继续传播
  return false;
});

/** 重试处理 */
function handleRetry() {
  hasError.value = false;
  errorMessage.value = '';
  errorStack.value = null;
  emit('retry');
}

/** 返回上一页 */
function handleBack() {
  window.history.back();
}
</script>

<template>
  <div class="error-boundary">
    <!-- 正常内容 -->
    <slot v-if="!showError"></slot>

    <!-- 错误降级 UI -->
    <div v-else class="error-boundary__fallback">
      <ElResult :icon="status" :title="errorMessage">
        <template v-if="showRetry || showBack" #extra>
          <div class="error-boundary__actions">
            <ElButton v-if="showRetry" type="primary" @click="handleRetry">
              重试
            </ElButton>
            <ElButton v-if="showBack" @click="handleBack">
              返回上一页
            </ElButton>
          </div>
        </template>

        <!-- 开发环境展示错误堆栈 -->
        <template v-if="showDetails && errorStack" #footer>
          <details class="error-boundary__details">
            <summary>错误详情</summary>
            <pre class="error-boundary__stack">{{ errorStack }}</pre>
          </details>
        </template>
      </ElResult>
    </div>
  </div>
</template>

<style scoped>
.error-boundary {
  width: 100%;
  height: 100%;
}

.error-boundary__fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  padding: 48px 24px;
}

.error-boundary__actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.error-boundary__details {
  margin-top: 24px;
  padding: 16px;
  background: var(--el-fill-color-light, #f5f7fa);
  border-radius: 6px;
  font-size: 12px;
}

.error-boundary__details summary {
  cursor: pointer;
  color: var(--el-text-color-secondary, #909399);
  margin-bottom: 8px;
  user-select: none;
}

.error-boundary__stack {
  margin: 0;
  padding: 12px;
  background: var(--el-bg-color, #fff);
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 11px;
  line-height: 1.6;
  color: var(--el-text-color-regular, #606266);
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
