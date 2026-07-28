<!--
 * ContextMenuPortal Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\context-menu\ContextMenuPortal.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { ContextMenuPortalProps } from 'radix-vue';

import { ContextMenuPortal } from 'radix-vue';

const props = defineProps<ContextMenuPortalProps>();
</script>

<template>
  <ContextMenuPortal v-bind="props">
    <slot></slot>
  </ContextMenuPortal>
</template>
