<!--
 * BreadcrumbLink Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\breadcrumb\BreadcrumbLink.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import type { PrimitiveProps } from 'radix-vue';

import { cn } from '@ydsz-core/shared/utils';

import { Primitive } from 'radix-vue';

const props = withDefaults(defineProps<PrimitiveProps & { class?: any }>(), {
  as: 'a',
});
</script>

<template>
  <Primitive
    :as="as"
    :as-child="asChild"
    :class="cn('hover:text-foreground transition-colors', props.class)"
  >
    <slot></slot>
  </Primitive>
</template>
