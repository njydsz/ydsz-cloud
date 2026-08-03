<!--
 * Qiankun 子应用挂载容器组件 — 作为微前端子应用的 DOM 挂载点
 *
 * 监听 Qiankun 生命周期（beforeLoad / afterMount）控制骨架屏切换，
 * 避免子应用切换时出现白屏。
 *
 * @path main\src\views\_core\subapp\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import { useRoute } from 'vue-router';

defineOptions({
  name: 'SubAppContainer',
});

const route = useRoute();
const isLoading = ref(false);

/** 从当前路由 path 提取子应用标识 */
function getAppPathPrefix() {
  return route.path.split('/').slice(0, 2).join('/') || '/';
}

onMounted(() => {
  const container = document.getElementById('subapp-container');
  if (!container) return;

  // 监听 Qiankun 生命周期
  const observer = new MutationObserver(() => {
    // 当子应用容器有内容时，隐藏 loading
    if (container.childElementCount > 0) {
      isLoading.value = false;
    }
  });

  observer.observe(container, { childList: true, subtree: false });

  // 监听路由变化，Qiankun 切换子应用前触发 loading
  const unsubscribe = route.afterEach?.(() => {
    isLoading.value = true;
  });

  onUnmounted(() => {
    observer.disconnect();
    if (typeof unsubscribe === 'function') unsubscribe();
  });
});
</script>

<template>
  <div class="subapp-wrapper">
    <!-- Qiankun 子应用挂载容器 -->
    <div id="subapp-container" class="subapp-container" :class="{ 'is-loading': isLoading }">
      <!-- 骨架屏（子应用加载中展示） -->
      <div v-if="isLoading" class="subapp-loading">
        <div class="loading-spinner">
          <div class="spinner-ring"></div>
        </div>
        <p class="loading-text">正在加载 {{ getAppPathPrefix() }} 模块...</p>
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

.subapp-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.loading-text {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  margin: 0;
}

.loading-spinner {
  position: relative;
  width: 40px;
  height: 40px;
}

.spinner-ring {
  width: 36px;
  height: 36px;
  border: 3px solid var(--el-border-color-lighter);
  border-top-color: var(--el-color-primary);
  border-radius: 50%;
  animation: subapp-spin 0.8s linear infinite;
}

@keyframes subapp-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
