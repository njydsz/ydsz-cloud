<!--
 * 应用根组件
 *
 * @path main\src\app.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import { ref } from 'vue';

import { useElementPlusDesignTokens } from '@ydsz/hooks';

import { ElConfigProvider } from 'element-plus';

import GlobalSearch from '#/components/global-search.vue';
import { elementLocale } from '#/locales';

defineOptions({ name: 'App' });

useElementPlusDesignTokens();

const searchVisible = ref(false);

// 暴露全局搜索触发器到 window（供 Header 组件调用）
if (typeof window !== 'undefined') {
  (window as any).__openGlobalSearch = () => {
    searchVisible.value = true;
  };
}
</script>

<template>
  <ElConfigProvider :locale="elementLocale">
    <RouterView />
    <GlobalSearch v-model:visible="searchVisible" />
  </ElConfigProvider>
</template>
