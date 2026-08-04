<!--
 * 子应用加载进度条（P0-2）
 *
 * 监听 micro-kernel 派发的 5 个生命周期事件，将子应用加载全链路可视化：
 * - before-load   → 30%  （开始拉取 ESM 入口）
 * - after-load    → 60%  （入口模块加载完成）
 * - before-mount  → 90%  （开始执行 mount 生命周期）
 * - after-mount   → 100% → 淡出
 * - error         → 错误态（红色条）→ 3s 后淡出
 *
 * 设计原则：
 * - 固定在视口顶部，z-index 高于 NetworkAlert (10000)
 * - 总高度 ~2px，不打断用户视觉焦点
 * - 渐变色从 primary → success，给用户"进度在推进"的心理暗示
 * - 事件驱动，自动启停，无需父组件手动控制
 *
 * @path main/src/components/subapp-progress.vue
 * @since 4.0.0
-->
<template>
  <Transition name="subapp-progress">
    <div
      v-if="visible"
      class="subapp-progress"
      :class="{ 'is-error': hasError }"
      role="progressbar"
      :aria-valuenow="progress"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-label="`${appName} 加载中 ${progress}%`"
    >
      <div class="bar" :style="{ width: `${progress}%` }" />
      <span v-if="showLabel" class="label">
        <LucideIcon :name="hasError ? 'lucide:alert-circle' : 'lucide:loader-2'" :size="12" class="icon" :class="{ spinning: !hasError }" />
        {{ labelText }}
      </span>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';

/** 进度阶段映射：事件 → 百分比 */
const STAGE_MAP: Record<string, number> = {
  'micro-kernel:before-load': 30,
  'micro-kernel:after-load': 60,
  'micro-kernel:before-mount': 90,
  'micro-kernel:after-mount': 100,
};

const visible = ref(false);
const progress = ref(0);
const appName = ref('');
const hasError = ref(false);

/** 加载中提示文案 */
const labelText = computed(() => {
  if (hasError.value) return `${appName.value} 加载失败`;
  if (progress.value < 40) return `正在加载 ${appName.value}...`;
  if (progress.value < 70) return `加载完成，准备挂载...`;
  if (progress.value < 100) return `正在初始化...`;
  return `挂载完成`;
});

/** 是否显示标签文案（加载时间 > 800ms 时让用户看到提示） */
const showLabel = ref(false);
let labelTimer: ReturnType<typeof setTimeout> | undefined;
/** 淡出定时器 */
let hideTimer: ReturnType<typeof setTimeout> | undefined;

function onLifecycleEvent(event: Event) {
  const detail = (event as CustomEvent<{ appName: string; error?: string }>).detail;
  if (!detail?.appName) return;

  // 清除上一次定时器
  clearTimeout(hideTimer!);
  clearTimeout(labelTimer!);

  appName.value = detail.appName;

  const eventName = event.type;

  if (eventName === 'micro-kernel:error') {
    hasError.value = true;
    progress.value = 100;
    visible.value = true;
    showLabel.value = true;
    // 错误态停留 3s 后淡出
    hideTimer = setTimeout(() => { hide(); }, 3000);
    return;
  }

  if (eventName === 'micro-kernel:before-load') {
    // 新一次加载周期重置
    hasError.value = false;
    showLabel.value = false;
    labelTimer = setTimeout(() => {
      if (!hasError.value && progress.value < 100) showLabel.value = true;
    }, 800);
  }

  progress.value = STAGE_MAP[eventName] ?? progress.value;
  visible.value = true;

  if (eventName === 'micro-kernel:after-mount') {
    // 完成态停留 600ms 后淡出
    hideTimer = setTimeout(() => { hide(); }, 600);
  }
}

function hide() {
  visible.value = false;
  progress.value = 0;
  hasError.value = false;
  appName.value = '';
  showLabel.value = false;
}

/** 监听的生命周期事件列表 */
const LIFECYCLE_EVENTS = [
  'micro-kernel:before-load',
  'micro-kernel:after-load',
  'micro-kernel:before-mount',
  'micro-kernel:after-mount',
  'micro-kernel:error',
] as const;

onMounted(() => {
  for (const evt of LIFECYCLE_EVENTS) {
    window.addEventListener(evt, onLifecycleEvent);
  }
});

onUnmounted(() => {
  for (const evt of LIFECYCLE_EVENTS) {
    window.removeEventListener(evt, onLifecycleEvent);
  }
  clearTimeout(hideTimer!);
  clearTimeout(labelTimer!);
});
</script>

<style scoped>
.subapp-progress {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 10001;
  height: 2px;
  background: transparent;
  overflow: visible;
}

.bar {
  height: 100%;
  background: linear-gradient(90deg, #409eff 0%, #67c23a 100%);
  transition: width 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 0 6px rgba(64, 158, 255, 0.4);
}

.is-error .bar {
  background: linear-gradient(90deg, #ff4d4f 0%, #ff7875 100%);
  box-shadow: 0 0 6px rgba(255, 77, 79, 0.4);
}

.label {
  position: absolute;
  top: 6px;
  right: 16px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  font-size: 11px;
  color: #409eff;
  background: #fff;
  border: 1px solid #d9ecff;
  border-radius: 4px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.06);
  white-space: nowrap;
}

.is-error .label {
  color: #ff4d4f;
  border-color: #ffccc7;
  background: #fff2f0;
}

.icon.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 淡入淡出 */
.subapp-progress-enter-active { transition: opacity 0.2s ease; }
.subapp-progress-leave-active { transition: opacity 0.3s ease; }
.subapp-progress-enter-from,
.subapp-progress-leave-to { opacity: 0; }
</style>
