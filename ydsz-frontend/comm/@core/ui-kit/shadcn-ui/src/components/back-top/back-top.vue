<!--
 * back-top 通用组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\components\back-top\back-top.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import type { BacktopProps } from './backtop';

import { computed } from 'vue';

import { ArrowUpToLine } from '@ydsz-core/icons';

import { YDSZButton } from '../button';
import { useBackTop } from './use-backtop';

interface Props extends BacktopProps {}

defineOptions({ name: 'BackTop' });

const props = withDefaults(defineProps<Props>(), {
  bottom: 20,
  isGroup: false,
  right: 24,
  target: '',
  visibilityHeight: 200,
});

const backTopStyle = computed(() => ({
  bottom: `${props.bottom}px`,
  right: `${props.right}px`,
}));

const { handleClick, visible } = useBackTop(props);
</script>
<template>
  <transition name="fade-down">
    <YDSZButton
      v-if="visible"
      :style="backTopStyle"
      class="dark:bg-accent dark:hover:bg-heavy bg-background hover:bg-heavy data shadow-float z-popup fixed bottom-10 size-10 rounded-full duration-500"
      size="icon"
      variant="icon"
      aria-label="返回顶部"
      @click="handleClick"
    >
      <ArrowUpToLine class="size-4" aria-hidden="true" />
    </YDSZButton>
  </transition>
</template>
