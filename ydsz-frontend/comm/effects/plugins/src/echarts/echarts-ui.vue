<!--
 * echarts-ui Vue 组件
 *
 * @path comm\effects\plugins\src\echarts\echarts-ui.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
interface Props {
  height?: string;
  width?: string;
}

withDefaults(defineProps<Props>(), {
  height: '300px',
  width: '100%',
});
</script>

<template>
  <div v-bind="$attrs" :style="{ height, width }"></div>
</template>
