<!--
 * Select Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\select\Select.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { SelectRootEmits, SelectRootProps } from 'radix-vue';

import { SelectRoot, useForwardPropsEmits } from 'radix-vue';

const props = defineProps<SelectRootProps>();
const emits = defineEmits<SelectRootEmits>();

const forwarded = useForwardPropsEmits(props, emits);
</script>

<template>
  <SelectRoot v-bind="forwarded">
    <slot></slot>
  </SelectRoot>
</template>
