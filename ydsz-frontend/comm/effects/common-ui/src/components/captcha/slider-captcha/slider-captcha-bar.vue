<!--
 * slider-captcha-bar 通用组件
 *
 * @path comm\effects\common-ui\src\components\captcha\slider-captcha\slider-captcha-bar.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { CSSProperties } from 'vue';

import { computed, ref, useTemplateRef } from 'vue';

const props = defineProps<{
  barStyle: CSSProperties;
  toLeft: boolean;
}>();

const barRef = useTemplateRef<HTMLDivElement>('barRef');

const width = ref('0');

const style = computed(() => {
  const { barStyle } = props;
  return {
    ...barStyle,
    width: width.value,
  };
});

defineExpose({
  getEl: () => {
    return barRef.value;
  },
  setWidth: (val: string) => {
    width.value = val;
  },
});
</script>

<template>
  <div
    ref="barRef"
    :class="toLeft && 'transition-width !w-0 duration-300'"
    :style="style"
    class="bg-primary absolute h-full"
  ></div>
</template>
