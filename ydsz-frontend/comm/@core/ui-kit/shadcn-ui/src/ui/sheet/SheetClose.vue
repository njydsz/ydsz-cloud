<!--
 * SheetClose Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\sheet\SheetClose.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { DialogCloseProps } from 'radix-vue';

import { DialogClose } from 'radix-vue';

const props = defineProps<DialogCloseProps>();
</script>

<template>
  <DialogClose v-bind="props">
    <slot></slot>
  </DialogClose>
</template>
