<!--
 * block 布局组件
 *
 * @path comm\effects\layouts\src\widgets\preferences\blocks\block.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
interface Props {
  title?: string;
}

defineOptions({
  name: 'PreferenceBlock',
});

withDefaults(defineProps<Props>(), {
  title: '',
});
</script>

<template>
  <div class="flex flex-col py-4">
    <h3 class="mb-3 font-semibold leading-none tracking-tight">
      {{ title }}
    </h3>
    <slot></slot>
  </div>
</template>
