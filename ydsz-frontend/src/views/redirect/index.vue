<!--
  @fileoverview 重定向中转页（用于刷新当前标签）
  @description 配合 TagsView 的"刷新当前"功能：
  *  1. 用户右键标签 → 选择"刷新当前"
  *  2. 跳转到 /redirect + 原 fullPath
  *  3. 本组件在 beforeRouteEnter 中立即 replace 回原 fullPath
  *  4. 由于原 fullPath 的组件实例已被销毁（路由切换），重新进入时触发重建 → 完成刷新
  *  全程无视觉闪烁（中转页不渲染任何 UI）
  * @module views/redirect
  * @author ydsz-pmis-team
  * @since 1.4.0
-->
<script setup lang="ts">
import { onBeforeMount } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

onBeforeMount(() => {
  // /redirect/xxx 形式的 path 参数会携带原始路径（含 query/hash）
  const { params, query } = route
  const targetPath = '/' + (Array.isArray(params.path) ? params.path.join('/') : params.path)
  router.replace({ path: targetPath, query })
})
</script>

<template>
  <div class="redirect-placeholder" aria-hidden="true" />
</template>

<style lang="scss" scoped>
.redirect-placeholder {
  width: 0;
  height: 0;
  overflow: hidden;
  visibility: hidden;
}
</style>
