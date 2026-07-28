<!--
 * FormItem Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\form\FormItem.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import { provide, useId } from 'vue';

import { cn } from '@ydsz-core/shared/utils';

import { FORM_ITEM_INJECTION_KEY } from './injectionKeys';

const props = defineProps<{
  class?: any;
}>();

const id = useId() as string;
provide(FORM_ITEM_INJECTION_KEY, id);
</script>

<template>
  <div :class="cn(props.class)">
    <slot></slot>
  </div>
</template>
