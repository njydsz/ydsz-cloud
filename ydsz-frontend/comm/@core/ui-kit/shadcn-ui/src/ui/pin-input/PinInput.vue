<!--
 * PinInput Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\pin-input\PinInput.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { PinInputRootEmits, PinInputRootProps } from 'radix-vue';

import { computed } from 'vue';

import { cn } from '@ydsz-core/shared/utils';

import { PinInputRoot, useForwardPropsEmits } from 'radix-vue';

const props = defineProps<PinInputRootProps & { class?: any }>();
const emits = defineEmits<PinInputRootEmits>();

const delegatedProps = computed(() => {
  const { class: _, ...delegated } = props;
  return delegated;
});

const forwarded = useForwardPropsEmits(delegatedProps, emits);
</script>

<template>
  <PinInputRoot
    v-bind="forwarded"
    :class="cn('flex items-center gap-2', props.class)"
  >
    <slot></slot>
  </PinInputRoot>
</template>
