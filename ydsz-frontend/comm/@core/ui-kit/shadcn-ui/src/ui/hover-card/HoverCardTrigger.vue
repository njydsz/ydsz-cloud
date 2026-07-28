<!--
 * HoverCardTrigger Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\hover-card\HoverCardTrigger.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { HoverCardTriggerProps } from 'radix-vue';

import { HoverCardTrigger } from 'radix-vue';

const props = defineProps<HoverCardTriggerProps>();
</script>

<template>
  <HoverCardTrigger v-bind="props">
    <slot></slot>
  </HoverCardTrigger>
</template>
