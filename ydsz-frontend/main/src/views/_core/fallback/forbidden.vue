<!--
 * forbidden 页面组件
 *
 * @path main\src\views\_core\fallback\forbidden.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import { Fallback } from '@ydsz/common-ui';

defineOptions({ name: 'Fallback403Demo' });
</script>

<template>
  <Fallback status="403" />
</template>
