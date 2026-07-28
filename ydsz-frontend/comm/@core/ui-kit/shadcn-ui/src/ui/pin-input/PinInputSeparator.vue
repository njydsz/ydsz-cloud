<!--
 * PinInputSeparator Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\pin-input\PinInputSeparator.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { PrimitiveProps } from 'radix-vue';

import { Dot } from 'lucide-vue-next';
import { Primitive, useForwardProps } from 'radix-vue';

const props = defineProps<PrimitiveProps>();
const forwardedProps = useForwardProps(props);
</script>

<template>
  <Primitive v-bind="forwardedProps">
    <slot>
      <Dot />
    </slot>
  </Primitive>
</template>
