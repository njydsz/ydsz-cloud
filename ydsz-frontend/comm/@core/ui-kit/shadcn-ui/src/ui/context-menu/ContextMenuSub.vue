<!--
 * ContextMenuSub Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\context-menu\ContextMenuSub.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { ContextMenuSubEmits, ContextMenuSubProps } from 'radix-vue';

import { ContextMenuSub, useForwardPropsEmits } from 'radix-vue';

const props = defineProps<ContextMenuSubProps>();
const emits = defineEmits<ContextMenuSubEmits>();

const forwarded = useForwardPropsEmits(props, emits);
</script>

<template>
  <ContextMenuSub v-bind="forwarded">
    <slot></slot>
  </ContextMenuSub>
</template>
