<!--
  @file 应用根组件
  @description PMIS 前端根组件，仅承担 RouterView 容器职责
  @module App
-->
<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <!-- el-config-provider 用于全局化 Element Plus 配置（国际化、尺寸、主题等） -->
  <el-config-provider>
    <RouterView />
  </el-config-provider>
</template>

<style lang="scss">
/* 应用根容器：固定占满视口，启用字体抗锯齿 */
#app {
  width: 100%;
  height: 100vh;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
</style>
