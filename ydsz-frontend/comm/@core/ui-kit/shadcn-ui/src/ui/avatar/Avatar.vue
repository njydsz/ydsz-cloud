<!--
 * Avatar Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\avatar\Avatar.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { AvatarVariants } from './avatar';

import { cn } from '@ydsz-core/shared/utils';

import { AvatarRoot } from 'radix-vue';

import { avatarVariant } from './avatar';

const props = withDefaults(
  defineProps<{
    class?: any;
    shape?: AvatarVariants['shape'];
    size?: AvatarVariants['size'];
  }>(),
  {
    shape: 'circle',
    size: 'sm',
  },
);
</script>

<template>
  <AvatarRoot :class="cn(avatarVariant({ size, shape }), props.class)">
    <slot></slot>
  </AvatarRoot>
</template>
