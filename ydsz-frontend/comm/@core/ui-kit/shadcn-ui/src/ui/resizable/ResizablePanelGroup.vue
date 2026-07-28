<!--
 * ResizablePanelGroup Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\resizable\ResizablePanelGroup.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { SplitterGroupEmits, SplitterGroupProps } from 'radix-vue';

import type { HTMLAttributes } from 'vue';

import { computed } from 'vue';

import { cn } from '@ydsz-core/shared/utils';

import { SplitterGroup, useForwardPropsEmits } from 'radix-vue';

const props = defineProps<
  SplitterGroupProps & { class?: HTMLAttributes['class'] }
>();
const emits = defineEmits<SplitterGroupEmits>();

const delegatedProps = computed(() => {
  const { class: _, ...delegated } = props;
  return delegated;
});

const forwarded = useForwardPropsEmits(delegatedProps, emits);
</script>

<template>
  <SplitterGroup
    v-bind="forwarded"
    :class="
      cn(
        'flex h-full w-full data-[panel-group-direction=vertical]:flex-col',
        props.class,
      )
    "
  >
    <slot></slot>
  </SplitterGroup>
</template>
