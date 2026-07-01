<!--
  @file 404 错误页
  @description 404 路由未匹配时展示的兜底错误页，提供返回首页入口，对应路由 * 兜底匹配。
  @module views/error/404
-->
<script setup lang="ts">
import { useRouter } from 'vue-router'

/** 路由实例，用于返回首页跳转 */
const router = useRouter()
</script>

<template>
  <div class="error-page">
    <el-result icon="warning" title="404" sub-title="抱歉，您访问的页面不存在">
      <template #extra>
        <el-button type="primary" @click="router.push('/')">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<style lang="scss" scoped>
.error-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $bg-page;
}
</style>
