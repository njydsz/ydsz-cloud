<!--
  @fileoverview 全局错误边界组件 (P0-E1 增强)
  @description 基于 Vue 3 onErrorCaptured 捕获子组件树渲染异常，避免白屏：
  - Props: resetOnRouteChange / maxRetry
  - Emits: error(子组件树异常时)
  - 监听路由变化自动重置；超出重试上限引导联系管理员
  - 生产环境自动上报 Sentry，开发环境展示详细错误
  @module components/common/ErrorBoundary
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
import { ref, computed, onErrorCaptured, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { captureError } from '@/utils/sentry'
import { logger } from '@/utils/logger'

/** 是否为开发环境（模板中无法直接使用 import.meta.env） */
const isDev = computed(() => import.meta.env.DEV)

const props = withDefaults(defineProps<{
  /** 路由切换时是否自动重置错误态（默认 true，顶层 ErrorBoundary 可设为 false） */
  resetOnRouteChange?: boolean
  /** 最大重试次数（默认 3，超过后引导联系管理员） */
  maxRetry?: number
}>(), {
  resetOnRouteChange: true,
  maxRetry: 3,
})

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

/**
 * 组件事件
 * - error: 子组件树抛出异常时触发，便于父组件做额外处理（如日志记录、上报等）
 */
const emit = defineEmits<{
  (e: 'error', err: unknown, info: string): void
}>()

/** 是否出现错误 */
const hasError = ref(false)
/** 错误信息（仅开发环境展示） */
const errorMessage = ref('')
/** 短错误 ID（生产环境展示，便于用户报障） */
const errorId = ref('')
/** 当前重试次数 */
const retryCount = ref(0)
/** 是否已达重试上限 */
const isLimitExceeded = ref(false)

/**
 * 生成短错误 ID（8 位 hex），用于生产环境用户报障
 */
function generateErrorId(): string {
  // 简单的 8 位随机 hex，不依赖 crypto（兼容性更好）
  return Array.from({ length: 8 }, () =>
    Math.floor(Math.random() * 16).toString(16)
  ).join('')
}

/**
 * onErrorCaptured 捕获子组件树抛出的异常
 * - 返回 false 阻止异常继续向上传播
 * - 生产环境通过 Sentry captureError 上报
 * - 向父组件 emit error 事件，便于上层感知
 */
onErrorCaptured((err, _instance, info) => {
  hasError.value = true
  errorMessage.value = err instanceof Error ? err.message : String(err)
  errorId.value = generateErrorId()

  // 通知父组件
  emit('error', err, info)

  // 生产环境上报到 Sentry（携带 errorId 便于关联）
  if (import.meta.env.PROD) {
    captureError(err, { componentTrace: info, errorId: errorId.value })
  } else {
    // 开发环境输出完整错误到 console
    logger.error('[ErrorBoundary]', err, { info, errorId: errorId.value })
  }

  // 阻止异常继续传播，避免白屏
  return false
})

/**
 * 监听路由变化自动重置错误态
 * - 避免 KeepAlive 缓存导致路由切换后仍显示错误
 * - 通过 props.resetOnRouteChange 可禁用（顶层 ErrorBoundary 不需要重置）
 */
watch(
  () => route.fullPath,
  () => {
    if (props.resetOnRouteChange && hasError.value) {
      hasError.value = false
      errorMessage.value = ''
      errorId.value = ''
      retryCount.value = 0
      isLimitExceeded.value = false
    }
  }
)

/** 用户点击"重试"重置错误状态 */
function handleRetry() {
  if (retryCount.value >= props.maxRetry) {
    isLimitExceeded.value = true
    return
  }
  retryCount.value++
  hasError.value = false
  errorMessage.value = ''
  errorId.value = ''
}

/** 用户点击"返回首页"（走 router.push，触发路由守卫与过渡动画） */
function handleGoHome() {
  hasError.value = false
  errorMessage.value = ''
  errorId.value = ''
  retryCount.value = 0
  isLimitExceeded.value = false
  router.push('/dashboard')
}

/** 复制错误 ID 到剪贴板（便于用户报障） */
async function handleCopyErrorId() {
  if (!errorId.value) return
  try {
    await navigator.clipboard.writeText(errorId.value)
    ElMessage.success(t('common.copyErrorId') + ': ' + errorId.value)
  } catch {
    // 降级：使用旧 execCommand
    const textarea = document.createElement('textarea')
    textarea.value = errorId.value
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success(t('common.copyErrorId') + ': ' + errorId.value)
  }
}
</script>

<template>
  <!-- 错误状态：展示降级 UI -->
  <div v-if="hasError" class="error-boundary">
    <div class="error-boundary__content">
      <el-result icon="error" :title="t('common.pageRenderError')" :sub-title="t('common.pageErrorSubtitle')">
        <template #extra>
          <template v-if="!isLimitExceeded">
            <el-button type="primary" @click="handleRetry">
              {{ t('common.retry') }}
              <span v-if="retryCount > 0" class="error-boundary__retry-count">({{ retryCount }}/{{ maxRetry }})</span>
            </el-button>
            <el-button @click="handleGoHome">{{ t('common.backHome') }}</el-button>
          </template>
          <template v-else>
            <el-alert :title="t('common.retryLimitExceeded')" type="warning" :closable="false" show-icon class="error-boundary__limit-alert" />
            <el-button type="primary" @click="handleGoHome">{{ t('common.backHome') }}</el-button>
          </template>
        </template>
      </el-result>

      <!-- 错误 ID（生产环境展示，便于报障） -->
      <div v-if="errorId" class="error-boundary__error-id">
        <span class="error-boundary__error-id-label">{{ t('common.errorId') }}:</span>
        <code class="error-boundary__error-id-value">{{ errorId }}</code>
        <el-button text size="small" @click="handleCopyErrorId">{{ t('common.copyErrorId') }}</el-button>
      </div>

      <!-- 错误详情：仅开发环境展示，避免生产环境泄露堆栈 -->
      <details class="error-boundary__details" v-if="errorMessage && isDev">
        <summary>{{ t('common.viewErrorDetails') }}</summary>
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

  &__retry-count {
    margin-left: 4px;
    font-size: $font-size-xs;
    opacity: 0.7;
  }

  &__limit-alert {
    margin-bottom: $spacing-md;
  }

  &__error-id {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    margin-top: $spacing-md;
    padding: $spacing-sm $spacing-md;
    background: $bg-base;
    border-radius: $border-radius-base;
    font-size: $font-size-sm;

    &-label {
      color: $text-secondary;
    }

    &-value {
      font-family: 'JetBrains Mono', 'Consolas', monospace;
      font-size: $font-size-sm;
      color: $danger-color;
      background: $bg-white;
      padding: 2px 6px;
      border-radius: $border-radius-sm;
      border: 1px solid $border-lighter;
    }
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
