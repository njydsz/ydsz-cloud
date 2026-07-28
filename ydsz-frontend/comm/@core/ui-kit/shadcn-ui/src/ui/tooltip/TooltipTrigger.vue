<!--
 * TooltipTrigger Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\tooltip\TooltipTrigger.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { TooltipTriggerProps } from 'radix-vue';

import { TooltipTrigger } from 'radix-vue';

const props = defineProps<TooltipTriggerProps>();
</script>

<template>
  <TooltipTrigger v-bind="props">
    <slot></slot>
  </TooltipTrigger>
</template>
