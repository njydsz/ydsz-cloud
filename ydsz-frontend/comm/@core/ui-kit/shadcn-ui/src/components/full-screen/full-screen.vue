<!--
 * full-screen 通用组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\components\full-screen\full-screen.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import { Maximize, Minimize } from '@ydsz-core/icons';

import { useFullscreen } from '@vueuse/core';

import { YDSZIconButton } from '../button';

defineOptions({ name: 'FullScreen' });

const { isFullscreen, toggle } = useFullscreen();

// 重新检查全屏状态
isFullscreen.value = !!(
  document.fullscreenElement ||
  // @ts-ignore
  document.webkitFullscreenElement ||
  // @ts-ignore
  document.mozFullScreenElement ||
  // @ts-ignore
  document.msFullscreenElement
);
</script>
<template>
  <YDSZIconButton @click="toggle">
    <Minimize v-if="isFullscreen" class="text-foreground size-4" />
    <Maximize v-else class="text-foreground size-4" />
  </YDSZIconButton>
</template>
