<!--
  @file 面包屑导航
  @description 基于当前路由的 route.matched 渲染面包屑，固定首页 → 当前路由
  @module layout/default/components/Breadcrumb
-->
<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <el-breadcrumb separator="/" class="breadcrumb">
    <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
    <el-breadcrumb-item v-for="item in route.matched" :key="item.path">
      {{ item.meta?.title || item.name }}
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<style lang="scss" scoped>
.breadcrumb {
  display: inline-block;
  font-size: $font-size-sm;
}
</style>
