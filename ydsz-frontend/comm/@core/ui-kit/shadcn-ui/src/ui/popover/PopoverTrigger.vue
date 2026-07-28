<!--
 * PopoverTrigger Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\popover\PopoverTrigger.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { PopoverTriggerProps } from 'radix-vue';

import { PopoverTrigger } from 'radix-vue';

const props = defineProps<PopoverTriggerProps>();
</script>

<template>
  <PopoverTrigger v-bind="props">
    <slot></slot>
  </PopoverTrigger>
</template>
