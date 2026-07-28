<!--
 * not-found 页面组件
 *
 * @path main\src\views\_core\fallback\not-found.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import { Fallback } from '@ydsz/common-ui';

defineOptions({ name: 'Fallback404Demo' });
</script>

<template>
  <Fallback status="404" />
</template>
