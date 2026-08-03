<!--
 * button 通用组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\components\button\button.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { YDSZButtonProps } from './button';

import { computed } from 'vue';

import { LoaderCircle } from '@ydsz-core/icons';
import { cn } from '@ydsz-core/shared/utils';

import { Primitive } from 'radix-vue';

import { buttonVariants } from '../../ui';

interface Props extends YDSZButtonProps {}

const props = withDefaults(defineProps<Props>(), {
  as: 'button',
  class: '',
  disabled: false,
  loading: false,
  size: 'default',
  variant: 'default',
});

const isDisabled = computed(() => {
  return props.disabled || props.loading;
});
</script>

<template>
  <Primitive
    :as="as"
    :as-child="asChild"
    :class="cn(buttonVariants({ variant, size }), props.class)"
    :disabled="isDisabled"
    :aria-disabled="isDisabled"
    :aria-busy="loading"
    role="button"
  >
    <LoaderCircle
      v-if="loading"
      class="text-md mr-2 size-4 flex-shrink-0 animate-spin"
      aria-hidden="true"
    />
    <slot></slot>
  </Primitive>
</template>
