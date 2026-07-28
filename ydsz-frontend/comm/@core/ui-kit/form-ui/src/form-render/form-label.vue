<!--
 * form-label Vue 组件
 *
 * @path comm\@core\ui-kit\form-ui\src\form-render\form-label.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { CustomRenderType } from '../types';

import {
  FormLabel,
  YDSZHelpTooltip,
  YDSZRenderContent,
} from '@ydsz-core/shadcn-ui';
import { cn } from '@ydsz-core/shared/utils';

interface Props {
  class?: string;
  colon?: boolean;
  help?: CustomRenderType;
  label?: CustomRenderType;
  required?: boolean;
}

const props = defineProps<Props>();
</script>

<template>
  <FormLabel :class="cn('flex items-center', props.class)">
    <span v-if="required" class="text-destructive mr-[2px]">*</span>
    <slot></slot>
    <YDSZHelpTooltip v-if="help" trigger-class="size-3.5 ml-1">
      <YDSZRenderContent :content="help" />
    </YDSZHelpTooltip>
    <span v-if="colon && label" class="ml-[2px]">:</span>
  </FormLabel>
</template>
