<!--
 * SheetTrigger Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\sheet\SheetTrigger.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { DialogTriggerProps } from 'radix-vue';

import { DialogTrigger } from 'radix-vue';

const props = defineProps<DialogTriggerProps>();
</script>

<template>
  <DialogTrigger v-bind="props">
    <slot></slot>
  </DialogTrigger>
</template>
