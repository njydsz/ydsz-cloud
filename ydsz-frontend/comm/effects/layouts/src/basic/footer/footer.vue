<!--
 * footer 布局组件
 *
 * @path comm\effects\layouts\src\basic\footer\footer.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
defineOptions({
  name: 'LayoutFooter',
});
</script>

<template>
  <div class="flex-center text-muted-foreground relative h-full w-full text-xs">
    <slot></slot>
  </div>
</template>
