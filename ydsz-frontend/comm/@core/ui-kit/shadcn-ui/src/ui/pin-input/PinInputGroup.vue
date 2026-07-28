<!--
 * PinInputGroup Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\pin-input\PinInputGroup.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { PrimitiveProps } from 'radix-vue';

import { computed } from 'vue';

import { cn } from '@ydsz-core/shared/utils';

import { Primitive, useForwardProps } from 'radix-vue';

const props = defineProps<PrimitiveProps & { class?: any }>();
const delegatedProps = computed(() => {
  const { class: _, ...delegated } = props;
  return delegated;
});
const forwardedProps = useForwardProps(delegatedProps);
</script>

<template>
  <Primitive
    v-bind="forwardedProps"
    :class="cn('flex items-center', props.class)"
  >
    <slot></slot>
  </Primitive>
</template>
