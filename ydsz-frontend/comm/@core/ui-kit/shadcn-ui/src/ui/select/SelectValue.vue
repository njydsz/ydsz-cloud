<!--
 * SelectValue Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\select\SelectValue.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { SelectValueProps } from 'radix-vue';

import { SelectValue } from 'radix-vue';

const props = defineProps<SelectValueProps>();
</script>

<template>
  <SelectValue v-bind="props">
    <slot></slot>
  </SelectValue>
</template>
