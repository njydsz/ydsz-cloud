<!--
 * layout-tabbar 通用组件
 *
 * @path comm\@core\ui-kit\layout-ui\src\components\layout-tabbar.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { CSSProperties } from 'vue';

import { computed } from 'vue';

interface Props {
  /**
   * 高度
   */
  height: number;
}

const props = withDefaults(defineProps<Props>(), {});

const style = computed((): CSSProperties => {
  const { height } = props;
  return {
    height: `${height}px`,
  };
});
</script>

<template>
  <section
    :style="style"
    class="border-border bg-background flex w-full border-b transition-all"
  >
    <slot></slot>
  </section>
</template>
