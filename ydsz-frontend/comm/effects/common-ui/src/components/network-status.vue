<!--
 * 网络状态监控组件 — 检测网络异常并展示友好提示
 *
 * 功能：
 * - 实时监测网络连接状态（online/offline）
 * - 网络恢复时自动隐藏提示
 * - 提供手动重试按钮
 * - 支持自定义提示文案和样式
 *
 * @example
 * ```vue
 * <NetworkStatus />
 * <NetworkStatus message="网络连接失败" show-retry @retry="reconnect" />
 * ```
 *
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';

import { ElAlert, ElButton } from 'element-plus';

defineOptions({ name: 'NetworkStatus' });

const props = withDefaults(
  defineProps<{
    /** 离线提示文案 */
    offlineMessage?: string;
    /** 是否展示重试按钮 */
    showRetry?: boolean;
    /** 提示类型 */
    type?: 'error' | 'warning' | 'info';
  }>(),
  {
    offlineMessage: '网络连接已断开，请检查网络设置',
    showRetry: true,
    type: 'error',
  },
);

const emit = defineEmits<{
  retry: [];
  online: [];
  offline: [];
}>();

const isOnline = ref(navigator.onLine);
const showBanner = ref(false);

/** 是否展示离线提示 */
const isOffline = computed(() => !isOnline.value && showBanner.value);

/** 网络状态变化处理 */
function handleOnline() {
  isOnline.value = true;
  showBanner.value = false;
  emit('online');
}

function handleOffline() {
  isOnline.value = false;
  showBanner.value = true;
  emit('offline');
}

/** 手动重试 */
function handleRetry() {
  emit('retry');
}

/** 关闭提示 */
function handleClose() {
  showBanner.value = false;
}

onMounted(() => {
  window.addEventListener('online', handleOnline);
  window.addEventListener('offline', handleOffline);

  // 初始状态检测
  if (!navigator.onLine) {
    showBanner.value = true;
  }
});

onUnmounted(() => {
  window.removeEventListener('online', handleOnline);
  window.removeEventListener('offline', handleOffline);
});
</script>

<template>
  <Transition name="network-status-fade">
    <div v-if="isOffline" class="network-status">
      <ElAlert
        :title="offlineMessage"
        :type="type"
        :closable="true"
        show-icon
        class="network-status__alert"
        @close="handleClose"
      >
        <template v-if="showRetry" #default>
          <div class="network-status__content">
            <span>请检查您的网络连接后重试。</span>
            <ElButton type="primary" size="small" @click="handleRetry">
              重试连接
            </ElButton>
          </div>
        </template>
      </ElAlert>
    </div>
  </Transition>
</template>

<style scoped>
.network-status {
  position: fixed;
  top: 16px;
  left: 50%;
  z-index: 9999;
  transform: translateX(-50%);
  width: 90%;
  max-width: 600px;
}

.network-status__alert {
  box-shadow: 0 4px 12px rgb(0 0 0 / 15%);
}

.network-status__content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

/* 过渡动画 */
.network-status-fade-enter-active,
.network-status-fade-leave-active {
  transition: all 0.3s ease;
}

.network-status-fade-enter-from,
.network-status-fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-20px);
}
</style>
