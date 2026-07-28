<!--
 * AlertDialogOverlay Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\alert-dialog\AlertDialogOverlay.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import { useScrollLock } from '@ydsz-core/composables';

useScrollLock();
</script>
<template>
  <div class="bg-overlay z-popup inset-0"></div>
</template>
