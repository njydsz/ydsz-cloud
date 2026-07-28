<!--
 * FormLabel Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\form\FormLabel.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import type { LabelProps } from 'radix-vue';

import { cn } from '@ydsz-core/shared/utils';

import { Label } from '../label';
import { useFormField } from './useFormField';

const props = defineProps<LabelProps & { class?: any }>();

const { formItemId } = useFormField();
</script>

<template>
  <Label :class="cn(props.class)" :for="formItemId">
    <slot></slot>
  </Label>
</template>
