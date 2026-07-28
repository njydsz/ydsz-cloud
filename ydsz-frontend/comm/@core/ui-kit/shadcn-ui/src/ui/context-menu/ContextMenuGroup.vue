<!--
 * ContextMenuGroup Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\context-menu\ContextMenuGroup.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { ContextMenuGroupProps } from 'radix-vue';

import { ContextMenuGroup } from 'radix-vue';

const props = defineProps<ContextMenuGroupProps>();
</script>

<template>
  <ContextMenuGroup v-bind="props">
    <slot></slot>
  </ContextMenuGroup>
</template>
