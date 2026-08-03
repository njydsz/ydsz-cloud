<!--
 * micro-kernel 微前端子应用挂载容器组件 — 作为子应用的 DOM 挂载点
 *
 * v3.2: 直接订阅 microRuntime 生命周期钩子（替代 window 事件），
 *       细化加载阶段（loading/mounting/mounted/error/unmounting），
 *       通过 unsubscribe 在组件卸载时彻底清理，避免泄漏。
 *
 * @path main\src\views\_core\subapp\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { MicroAppConfig } from '@ydsz/micro-runtime';
import type { Component } from 'vue';

import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { microRuntime } from '#/bootstrap';

import { getSkeletonComponent, type SkeletonType } from './skeletons/skeleton-registry';

defineOptions({
  name: 'SubAppContainer',
});

/**
 * 子应用加载阶段 — 由内核生命周期钩子驱动。
 *
 * 注：内核 beforeLoad 在 loadApp + mount 之前触发，afterMount 在两者均完成后触发，
 * 因此本组件无法细分 "ESM 加载完" 与 "mount() 完成"，合并为 loading 阶段。
 */
type LoadingPhase = 'error' | 'idle' | 'loading' | 'mounted' | 'unmounting';

/** 当前阶段 */
const phase = ref<LoadingPhase>('idle');
/** 正在加载/激活的子应用名（来自 hook 入参，避免猜测路由） */
const activeAppName = ref<null | string>(null);
/** 进度百分比（按阶段映射，非随机模拟） */
const progress = ref(0);
/** 阶段对应的提示文案 */
const phaseText = ref('');
/** 最近一次错误信息（用于错误态展示） */
const lastError = ref<null | string>(null);

const state = reactive({ phase, activeAppName, progress, phaseText, lastError });

/** 路由实例，用于读取 meta.skeletonType */
const route = useRoute();

/**
 * 根据当前路由 meta.skeletonType 动态返回骨架屏组件。
 *
 * 路由配置示例：
 *   { path: '/users', component: UserList, meta: { skeletonType: 'list' } }
 *   { path: '/dashboard', component: Dashboard, meta: { skeletonType: 'dashboard' } }
 *   { path: '/form', component: Form, meta: { skeletonType: 'form' } }
 *   { path: '/detail/:id', component: Detail, meta: { skeletonType: 'detail' } }
 */
const pageSkeletonComponent = computed<Component>(() => {
  const skeletonType = (route.meta?.skeletonType as SkeletonType) || 'default';
  return getSkeletonComponent(skeletonType);
});

/** 阶段 → (百分比, 文案) 映射表，保证进度反映真实生命周期而非随机数 */
const PHASE_META: Record<LoadingPhase, readonly [number, string]> = {
  idle: [0, ''],
  loading: [30, '正在加载模块...'],
  mounted: [100, '加载完成'],
  unmounting: [50, '正在切换应用...'],
  error: [0, '加载失败'],
};

function setPhase(next: LoadingPhase, appName: null | string = activeAppName.value): void {
  const [pct, text] = PHASE_META[next];
  state.phase = next;
  state.progress = pct;
  state.phaseText = text;
  if (appName !== undefined) state.activeAppName = appName;
  if (next !== 'error') state.lastError = null;
}

/** 取消订阅函数集合，组件卸载时统一调用 */
const unsubscribers: Array<() => void> = [];

onMounted(() => {
  if (!microRuntime) {
    // 内核尚未初始化（理论上 bootstrap 已同步注册，防御性处理）
    console.warn('[SubAppContainer] microRuntime not ready');
    return;
  }

  // beforeLoad: 子应用开始加载 ESM 模块
  unsubscribers.push(
    microRuntime.addLifecycleHook('beforeLoad', (app: MicroAppConfig) => {
      setPhase('loading', app.name);
    }),
  );

  // afterMount: 子应用 mount() 完成，DOM 已挂载
  unsubscribers.push(
    microRuntime.addLifecycleHook('afterMount', (app: MicroAppConfig) => {
      setPhase('mounted', app.name);
      // 100% 后短暂保持，再切回 idle 以便复用
      window.setTimeout(() => {
        if (state.phase === 'mounted') setPhase('idle');
      }, 300);
    }),
  );

  // afterUnmount: 子应用卸载完成（切换中的过渡态）
  unsubscribers.push(
    microRuntime.addLifecycleHook('afterUnmount', (app: MicroAppConfig) => {
      // 若当前激活应用仍是被卸载的应用，进入 unmounting 过渡
      if (state.activeAppName === app.name) {
        setPhase('unmounting', null);
      }
    }),
  );

  // error: 加载或挂载失败
  unsubscribers.push(
    microRuntime.addLifecycleHook('error', (app: MicroAppConfig, err: unknown) => {
      state.lastError = err instanceof Error ? err.message : String(err);
      setPhase('error', app.name);
    }),
  );

  // 兜底：若初始路由已命中子应用但 beforeLoad 触发晚于组件挂载，
  // 通过当前激活应用名回填一次状态
  const active = microRuntime.getActiveAppName();
  if (active && state.phase === 'idle') {
    setPhase('mounted', active);
  }
});

onUnmounted(() => {
  for (const off of unsubscribers.splice(0)) {
    try {
      off();
    } catch {
      /* 静默 */
    }
  }
});

/** 是否展示骨架屏（loading/unmounting） */
function showSkeleton(): boolean {
  return ['loading', 'unmounting'].includes(state.phase);
}

/** 是否展示错误态（错误由内核 error-boundary 渲染容器内 fallback，此处仅作背景遮罩） */
function showErrorMask(): boolean {
  return state.phase === 'error';
}
</script>

<template>
  <div class="subapp-wrapper">
    <!-- 子应用挂载容器 -->
    <div
      id="subapp-container"
      class="subapp-container"
      :class="{ 'is-loading': showSkeleton(), 'has-error': showErrorMask() }"
    >
      <!-- 页面级骨架屏（根据路由 meta.skeletonType 动态加载） -->
      <div v-if="showSkeleton()" class="subapp-skeleton-wrapper">
        <component :is="pageSkeletonComponent" />
        <div class="skeleton-progress">
          <div class="progress-bar" :style="{ width: `${state.progress}%` }"></div>
        </div>
        <p class="loading-text">
          {{ state.phaseText }}<span v-if="state.activeAppName"> · {{ state.activeAppName }}</span>
        </p>
      </div>

      <!-- 错误态遮罩（实际错误 UI 由内核 error-boundary 渲染） -->
      <div v-else-if="showErrorMask()" class="subapp-error-mask" aria-live="polite">
        <p class="error-app">{{ state.activeAppName }}</p>
        <p class="error-msg">{{ state.lastError || '加载失败' }}</p>
        <p class="error-hint">已降级为整页访问，请稍后重试</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.subapp-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
}

.subapp-container {
  width: 100%;
  height: 100%;
  min-height: 400px;
}

.subapp-container.is-loading {
  display: flex;
  align-items: center;
  justify-content: center;
}

.subapp-container.has-error {
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 骨架屏包装器样式 */
.subapp-skeleton-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  padding: 40px;
  gap: 24px;
}

.skeleton-progress {
  width: 100%;
  max-width: 600px;
  height: 4px;
  background: #f0f0f0;
  border-radius: 2px;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  background: linear-gradient(90deg, var(--el-color-primary-light-5, #409eff) 0%, var(--el-color-primary, #409eff) 100%);
  border-radius: 2px;
  transition: width 0.3s ease;
}

.loading-text {
  color: var(--el-text-color-secondary, #909399);
  font-size: 14px;
  margin: 0;
  text-align: center;
}

/* 错误态遮罩样式 */
.subapp-error-mask {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px;
  color: var(--el-text-color-secondary, #909399);
  text-align: center;
}

.error-app {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
  margin: 0;
}

.error-msg {
  font-size: 14px;
  margin: 0;
  word-break: break-all;
}

.error-hint {
  font-size: 12px;
  color: var(--el-text-color-placeholder, #a8abb2);
  margin: 0;
}

@keyframes skeleton-loading {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}
</style>
