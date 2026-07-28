<!--
 * internal-error 页面组件
 *
 * @path main\src\views\_core\fallback\internal-error.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import { Fallback } from '@ydsz/common-ui';

defineOptions({ name: 'Fallback500Demo' });
</script>

<template>
  <Fallback status="500" />
</template>
