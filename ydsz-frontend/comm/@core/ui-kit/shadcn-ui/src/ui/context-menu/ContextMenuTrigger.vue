<!--
 * ContextMenuTrigger Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\context-menu\ContextMenuTrigger.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { ContextMenuTriggerProps } from 'radix-vue';

import { ContextMenuTrigger, useForwardProps } from 'radix-vue';

const props = defineProps<ContextMenuTriggerProps>();

const forwardedProps = useForwardProps(props);
</script>

<template>
  <ContextMenuTrigger v-bind="forwardedProps">
    <slot></slot>
  </ContextMenuTrigger>
</template>
