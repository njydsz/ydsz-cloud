<!--
 * tooltip 通用组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\components\tooltip\tooltip.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { TooltipContentProps } from 'radix-vue';

import type { StyleValue } from 'vue';

import type { ClassType } from '@ydsz-core/typings';

import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '../../ui';

interface Props {
  contentClass?: ClassType;
  contentStyle?: StyleValue;
  delayDuration?: number;
  side?: TooltipContentProps['side'];
}

withDefaults(defineProps<Props>(), {
  delayDuration: 0,
  side: 'right',
});
</script>

<template>
  <TooltipProvider :delay-duration="delayDuration">
    <Tooltip>
      <TooltipTrigger as-child>
        <slot name="trigger"></slot>
      </TooltipTrigger>
      <TooltipContent
        :class="contentClass"
        :side="side"
        :style="contentStyle"
        class="side-content text-popover-foreground bg-accent rounded-md"
        role="tooltip"
      >
        <slot></slot>
      </TooltipContent>
    </Tooltip>
  </TooltipProvider>
</template>
