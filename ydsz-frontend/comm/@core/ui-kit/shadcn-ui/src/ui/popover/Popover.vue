<!--
 * Popover Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\popover\Popover.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { PopoverRootEmits, PopoverRootProps } from 'radix-vue';

import { PopoverRoot, useForwardPropsEmits } from 'radix-vue';

const props = defineProps<PopoverRootProps>();
const emits = defineEmits<PopoverRootEmits>();

const forwarded = useForwardPropsEmits(props, emits);
</script>

<template>
  <PopoverRoot v-bind="forwarded">
    <slot></slot>
  </PopoverRoot>
</template>
