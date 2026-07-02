<!--
  @file 全局错误边界组件（P2-1）
  @description 基于 Vue 3 onErrorCaptured 捕获子组件树渲染异常，
               避免未捕获异常导致白屏。生产环境自动上报到 Sentry。
  @module components/common/ErrorBoundary
-->
<script setup lang="ts">
import { ref, onErrorCaptured } from 'vue'
import { captureError } from '@/utils/sentry'

/** 是否出现错误 */
const hasError = ref(false)
/** 错误信息 */
const errorMessage = ref('')

/**
 * onErrorCaptured 捕获子组件树抛出的异常
 * - 返回 false 阻止异常继续向上传播
 * - 生产环境通过 Sentry captureError 上报
 */
onErrorCaptured((err, _instance, info) => {
  hasError.value = true
  errorMessage.value = err instanceof Error ? err.message : String(err)

  // 生产环境上报到 Sentry
  if (import.meta.env.PROD) {
    captureError(err, { componentTrace: info })
  }

  // 阻止异常继续传播，避免白屏
  return false
})

/** 用户点击"重试"重置错误状态 */
function handleRetry() {
  hasError.value = false
  errorMessage.value = ''
}

/** 用户点击"返回首页" */
function handleGoHome() {
  hasError.value = false
  errorMessage.value = ''
  window.location.hash = '#/dashboard'
}
</script>

<template>
  <!-- 错误状态：展示降级 UI -->
  <div v-if="hasError" class="error-boundary">
    <div class="error-boundary__content">
      <el-result icon="error" title="页面渲染异常" sub-title="抱歉，页面发生了未知错误，请重试或返回首页">
        <template #extra>
          <el-button type="primary" @click="handleRetry">重试</el-button>
          <el-button @click="handleGoHome">返回首页</el-button>
        </template>
      </el-result>
      <details class="error-boundary__details" v-if="errorMessage">
        <summary>查看错误详情</summary>
        <pre class="error-boundary__trace">{{ errorMessage }}</pre>
      </details>
    </div>
  </div>
  <!-- 正常状态：渲染子组件 -->
  <slot v-else />
</template>

<style lang="scss" scoped>
.error-boundary {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  padding: $spacing-lg;

  &__content {
    max-width: 600px;
    width: 100%;
  }

  &__details {
    margin-top: $spacing-md;
    padding: $spacing-md;
    background: $bg-base;
    border-radius: $border-radius-base;

    summary {
      cursor: pointer;
      color: $text-secondary;
      font-size: $font-size-sm;
    }
  }

  &__trace {
    margin-top: $spacing-sm;
    padding: $spacing-sm;
    background: $bg-white;
    border: 1px solid $border-lighter;
    border-radius: $border-radius-sm;
    font-family: 'JetBrains Mono', 'Consolas', monospace;
    font-size: $font-size-xs;
    color: $danger-color;
    white-space: pre-wrap;
    word-break: break-all;
    max-height: 200px;
    overflow: auto;
  }
}
</style>
