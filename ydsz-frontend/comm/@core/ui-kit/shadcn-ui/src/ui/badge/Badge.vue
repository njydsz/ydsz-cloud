<!--
 * Badge Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\badge\Badge.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { BadgeVariants } from './badge';

import { cn } from '@ydsz-core/shared/utils';

import { badgeVariants } from './badge';

const props = defineProps<{
  class?: any;
  variant?: BadgeVariants['variant'];
}>();
</script>

<template>
  <div :class="cn(badgeVariants({ variant }), props.class)">
    <slot></slot>
  </div>
</template>
