<!--
 * 应用根组件
 *
 * @path main\src\app.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<!--
 * 应用根组件（v4.0 增强）
 *
 * 新增：
 * - NetworkAlert 网络状态顶部条（断网/慢速/省流量提示）
 * - 快捷键 cmd+k 触发全局搜索（通过 useKeyboard 中枢注册）
 *
 * @path main\src\app.vue
 * @since 4.0.0
-->
<script lang="ts" setup>
import { ref, onMounted, onUnmounted } from 'vue';

import { useElementPlusDesignTokens } from '@ydsz/hooks';

import { ElConfigProvider } from 'element-plus';
import { preferences } from '@ydsz/preferences';

import GlobalSearch from '#/components/global-search.vue';
import NetworkAlert from '#/components/network-alert.vue';
import SubAppProgress from '#/components/subapp-progress.vue';
import { elementLocale } from '#/locales';
import { registerKeyboard } from '#/hooks/use-global-shortcut';

defineOptions({ name: 'App' });

useElementPlusDesignTokens();

const searchVisible = ref(false);

// v4.0: 通过快捷键中枢注册 cmd+k（统一管理，自动冲突检测）
let stopSearchShortcut: (() => void) | undefined;
onMounted(() => {
  stopSearchShortcut = registerKeyboard('cmd+k', (e) => {
    e.preventDefault();
    searchVisible.value = !searchVisible.value;
  });
});
onUnmounted(() => { stopSearchShortcut?.(); });
</script>

<template>
  <ElConfigProvider :locale="elementLocale">
    <NetworkAlert />
    <SubAppProgress v-if="preferences.transition.progress" />
    <RouterView />
    <GlobalSearch v-model:visible="searchVisible" />
  </ElConfigProvider>
</template>
