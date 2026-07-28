<!--
 * Tabs Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\tabs\Tabs.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { TabsRootEmits, TabsRootProps } from 'radix-vue';

import { TabsRoot, useForwardPropsEmits } from 'radix-vue';

const props = defineProps<TabsRootProps>();
const emits = defineEmits<TabsRootEmits>();

const forwarded = useForwardPropsEmits(props, emits);
</script>

<template>
  <TabsRoot v-bind="forwarded">
    <slot></slot>
  </TabsRoot>
</template>
