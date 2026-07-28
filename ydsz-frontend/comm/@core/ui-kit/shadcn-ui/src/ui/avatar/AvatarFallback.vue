<!--
 * AvatarFallback Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\avatar\AvatarFallback.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { AvatarFallbackProps } from 'radix-vue';

import { AvatarFallback } from 'radix-vue';

const props = defineProps<AvatarFallbackProps>();
</script>

<template>
  <AvatarFallback v-bind="props">
    <slot></slot>
  </AvatarFallback>
</template>
