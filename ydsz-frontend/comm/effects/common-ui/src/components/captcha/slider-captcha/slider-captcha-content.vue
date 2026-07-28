<!--
 * slider-captcha-content 通用组件
 *
 * @path comm\effects\common-ui\src\components\captcha\slider-captcha\slider-captcha-content.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { CSSProperties } from 'vue';

import { computed, useTemplateRef } from 'vue';

import { YDSZSpineText } from '@ydsz-core/shadcn-ui';

const props = defineProps<{
  contentStyle: CSSProperties;
  isPassing: boolean;
  successText: string;
  text: string;
}>();

const contentRef = useTemplateRef<HTMLDivElement>('contentRef');

const style = computed(() => {
  const { contentStyle } = props;

  return {
    ...contentStyle,
  };
});

defineExpose({
  getEl: () => {
    return contentRef.value;
  },
});
</script>

<template>
  <div
    ref="contentRef"
    :class="{
      [$style.success]: isPassing,
    }"
    :style="style"
    class="absolute top-0 flex size-full select-none items-center justify-center text-xs"
  >
    <slot name="text">
      <YDSZSpineText class="flex h-full items-center">
        {{ isPassing ? successText : text }}
      </YDSZSpineText>
    </slot>
  </div>
</template>

<style module>
.success {
  -webkit-text-fill-color: hsl(0deg 0% 98%);
}
</style>
