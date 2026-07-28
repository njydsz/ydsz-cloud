<!--
 * Breadcrumb Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\breadcrumb\Breadcrumb.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
const props = defineProps<{
  class?: any;
}>();
</script>

<template>
  <nav :class="props.class" aria-label="breadcrumb" role="navigation">
    <slot></slot>
  </nav>
</template>
