<!--
 * SelectItemText Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\select\SelectItemText.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { SelectItemTextProps } from 'radix-vue';

import { SelectItemText } from 'radix-vue';

const props = defineProps<SelectItemTextProps>();
</script>

<template>
  <SelectItemText v-bind="props">
    <slot></slot>
  </SelectItemText>
</template>
