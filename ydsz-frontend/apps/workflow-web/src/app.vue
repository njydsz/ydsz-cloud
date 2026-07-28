<!--
 * 应用根组件
 *
 * @path apps\workflow-web\src\app.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import { useElementPlusDesignTokens } from '@ydsz/hooks';

import { ElConfigProvider } from 'element-plus';

import { elementLocale } from '#/locales';

defineOptions({ name: 'App' });

useElementPlusDesignTokens();
</script>

<template>
  <ElConfigProvider :locale="elementLocale">
    <RouterView />
  </ElConfigProvider>
</template>
