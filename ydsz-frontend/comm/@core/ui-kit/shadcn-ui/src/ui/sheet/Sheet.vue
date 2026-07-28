<!--
 * Sheet Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\sheet\Sheet.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { DialogRootEmits, DialogRootProps } from 'radix-vue';

import { DialogRoot, useForwardPropsEmits } from 'radix-vue';

const props = defineProps<DialogRootProps>();
const emits = defineEmits<DialogRootEmits>();

const forwarded = useForwardPropsEmits(props, emits);
</script>

<template>
  <DialogRoot v-bind="forwarded">
    <slot></slot>
  </DialogRoot>
</template>
