<!--
 * DialogOverlay Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\dialog\DialogOverlay.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import { inject } from 'vue';

import { useScrollLock } from '@ydsz-core/composables';

useScrollLock();
const id = inject('DISMISSABLE_MODAL_ID');
</script>
<template>
  <div :data-dismissable-modal="id" class="bg-overlay z-popup inset-0"></div>
</template>
