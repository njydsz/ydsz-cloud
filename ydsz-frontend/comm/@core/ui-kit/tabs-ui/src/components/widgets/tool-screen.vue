<!--
 * tool-screen 通用组件
 *
 * @path comm\@core\ui-kit\tabs-ui\src\components\widgets\tool-screen.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import { Fullscreen, Minimize2 } from '@ydsz-core/icons';

const screen = defineModel<boolean>('screen');

function toggleScreen() {
  screen.value = !screen.value;
}
</script>

<template>
  <div
    class="flex-center hover:bg-muted hover:text-foreground text-muted-foreground border-border h-full cursor-pointer border-l px-2 text-lg font-semibold"
    @click="toggleScreen"
  >
    <Minimize2 v-if="screen" class="size-4" />
    <Fullscreen v-else class="size-4" />
  </div>
</template>
