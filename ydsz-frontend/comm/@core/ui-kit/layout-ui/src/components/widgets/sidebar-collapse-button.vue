<script setup lang="ts">
import { ChevronsLeft, ChevronsRight } from '@ydsz-core/icons';

const collapsed = defineModel<boolean>('collapsed');

function handleCollapsed() {
  collapsed.value = !collapsed.value;
}
</script>

<template>
  <div
    :style="{
      transform: 'translateY(-50%)',
    }"
    class="flex-center hover:text-foreground text-foreground/60 hover:bg-accent bg-sidebar border-border absolute -right-4 top-[50%] z-10 h-56 w-4 cursor-pointer rounded-r-full border"
    @click.stop="handleCollapsed"
  >
    <ChevronsRight v-if="collapsed" class="size-4" />
    <ChevronsLeft v-else class="size-4" />
  </div>
</template>
