<!--
 * TooltipProvider Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\tooltip\TooltipProvider.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { TooltipProviderProps } from 'radix-vue';

import { TooltipProvider } from 'radix-vue';

const props = defineProps<TooltipProviderProps>();
</script>

<template>
  <TooltipProvider v-bind="props">
    <slot></slot>
  </TooltipProvider>
</template>
