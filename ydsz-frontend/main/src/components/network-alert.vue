<!--
 * 网络状态反馈组件（P1-3）
 * 响应式追踪网络状态，条件触发顶部提示：
 * - 离线：红色提示，提供「重试」按钮
 * - 慢速/2g：黄色提示
 * - 省流量模式：蓝色提示
 * - 网络恢复：绿色提示（3s 后自动消失）
 *
 * @path main/src/components/network-alert.vue
 * @since 4.0.0
-->
<template>
  <Transition name="network-alert">
    <div
      v-if="visible"
      :class="['network-alert', `is-${severity}`]"
      role="alert"
      aria-live="assertive"
      :aria-label="text"
    >
      <LucideIcon :name="iconName" :size="16" class="icon" />
      <span class="text">{{ text }}</span>
      <button v-if="severity === 'error'" class="retry" @click="handleRetry">重试</button>
      <button v-else class="close" aria-label="关闭" @click="justRecovered = false">×</button>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useNetworkStatus } from '@/hooks/use-network-status';

const { networkStatus } = useNetworkStatus();
const justRecovered = ref(false);

const severity = computed<'info' | 'warning' | 'error' | 'success' | 'none'>(() => {
  if (!networkStatus.value.isOnline) return 'error';
  if (networkStatus.value.isSaveData) return 'warning';
  if (networkStatus.value.effectiveType === '2g') return 'warning';
  return 'none';
});

const visible = computed(() => severity.value !== 'none' || justRecovered.value);

const text = computed(() => {
  if (justRecovered.value) return '网络已恢复';
  switch (severity.value) {
    case 'error': return '网络已断开，部分操作可能失败';
    case 'warning': return networkStatus.value.isSaveData
      ? '已开启省流量模式，部分动画与预加载已禁用'
      : '当前网络较慢，加载可能延迟';
    default: return '';
  }
});

const iconName = computed(() => {
  if (justRecovered.value) return 'lucide:wifi';
  switch (severity.value) {
    case 'error': return 'lucide:wifi-off';
    case 'warning': return 'lucide:alert-triangle';
    default: return '';
  }
});

const handleRetry = () => { window.location.reload(); };

watch(() => networkStatus.value.isOnline, (online, prev) => {
  if (online && prev === false) {
    justRecovered.value = true;
    setTimeout(() => { justRecovered.value = false; }, 3000);
  }
});
</script>

<style scoped>
.network-alert {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 12px;
  font-weight: 500;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
}
.network-alert.is-error { color: #ff4d4f; background: #fff2f0; border-bottom: 1px solid #ffccc7; }
.network-alert.is-warning { color: #faad14; background: #fffbe6; border-bottom: 1px solid #ffe58f; }
.network-alert.is-success { color: #52c41a; background: #f6ffed; border-bottom: 1px solid #b7eb8f; }
.icon { flex-shrink: 0; }
.text { flex: 1; max-width: 600px; }
.retry, .close {
  padding: 2px 8px;
  border: 1px solid currentColor;
  border-radius: 4px;
  background: transparent;
  cursor: pointer;
  color: inherit;
  font-size: 12px;
}
.close { border: none; font-size: 16px; line-height: 1; padding: 0 4px; }
.network-alert-enter-active, .network-alert-leave-active { transition: all 0.25s ease; }
.network-alert-enter-from, .network-alert-leave-to { opacity: 0; transform: translateY(-100%); }
</style>
