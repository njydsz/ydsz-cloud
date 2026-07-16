<!--
  @fileoverview 面包屑导航
  @description 基于当前路由的 route.matched 渲染面包屑：
  - 固定首页 → 当前路由链路
  - 自动识别 i18n key（以 route. 开头则翻译，否则原样）
  @module layout/default/components/Breadcrumb
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
import { useRoute } from 'vue-router'
import i18n from '@/locales'

const route = useRoute()

/** 解析路由标题：i18n key 则翻译，否则原样返回 */
function resolveTitle(title: string | undefined, fallback: string): string {
  if (!title) return fallback
  return title.startsWith('route.') ? i18n.global.t(title) : title
}
</script>

<template>
  <el-breadcrumb separator="/" class="breadcrumb">
    <el-breadcrumb-item :to="{ path: '/dashboard' }">{{ resolveTitle('route.dashboard', i18n.global.t('common.home')) }}</el-breadcrumb-item>
    <el-breadcrumb-item v-for="item in route.matched" :key="item.path">
      {{ resolveTitle(item.meta?.title as string, String(item.name)) }}
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<style lang="scss" scoped>
.breadcrumb {
  display: inline-block;
  font-size: $font-size-sm;
}
</style>
