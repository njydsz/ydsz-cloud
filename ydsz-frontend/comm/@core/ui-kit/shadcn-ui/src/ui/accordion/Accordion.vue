<!--
 * Accordion Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\accordion\Accordion.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { AccordionRootEmits, AccordionRootProps } from 'radix-vue';

import { AccordionRoot, useForwardPropsEmits } from 'radix-vue';

const props = defineProps<AccordionRootProps>();
const emits = defineEmits<AccordionRootEmits>();

const forwarded = useForwardPropsEmits(props, emits);
</script>

<template>
  <AccordionRoot v-bind="forwarded">
    <slot></slot>
  </AccordionRoot>
</template>
