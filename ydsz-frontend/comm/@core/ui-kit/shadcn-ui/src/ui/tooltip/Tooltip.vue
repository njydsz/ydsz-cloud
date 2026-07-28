<!--
 * Tooltip Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\tooltip\Tooltip.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { TooltipRootEmits, TooltipRootProps } from 'radix-vue';

import { TooltipRoot, useForwardPropsEmits } from 'radix-vue';

const props = defineProps<TooltipRootProps>();
const emits = defineEmits<TooltipRootEmits>();

const forwarded = useForwardPropsEmits(props, emits);
</script>

<template>
  <TooltipRoot v-bind="forwarded">
    <slot></slot>
  </TooltipRoot>
</template>
