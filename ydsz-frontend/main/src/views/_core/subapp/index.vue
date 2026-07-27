<script setup lang="ts">
defineOptions({
  name: 'SubAppContainer',
});
</script>

<template>
  <div class="subapp-wrapper">
    <!-- Qiankun 子应用挂载容器 -->
    <div id="subapp-container" class="subapp-container"></div>
  </div>
</template>

<style scoped>
.subapp-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
}

.subapp-container {
  width: 100%;
  height: 100%;
  min-height: 400px;
}
</style>
