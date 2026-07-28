<!--
 * Button Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\button\Button.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { PrimitiveProps } from 'radix-vue';

import type { ButtonVariants, ButtonVariantSize } from './types';

import { cn } from '@ydsz-core/shared/utils';

import { Primitive } from 'radix-vue';

import { buttonVariants } from './button';

interface Props extends PrimitiveProps {
  class?: any;
  size?: ButtonVariantSize;
  variant?: ButtonVariants;
}

const props = withDefaults(defineProps<Props>(), {
  as: 'button',
  class: '',
});
</script>

<template>
  <Primitive
    :as="as"
    :as-child="asChild"
    :class="cn(buttonVariants({ variant, size }), props.class)"
  >
    <slot></slot>
  </Primitive>
</template>
