<!--
  @file 500 错误页
  @description 服务端异常或路由懒加载失败（chunk load error）时展示的兜底错误页，
               提供返回首页与刷新重试入口；由 router.onError 跳转或用户手动访问 /500。
  @module views/error/500
-->
<script setup lang="ts">
import { useRouter } from 'vue-router'

/** 路由实例，用于返回首页跳转 */
const router = useRouter()

/** 刷新当前页：通过 location.reload 触发整页重载，规避 chunk 缓存问题 */
function reload() {
  window.location.reload()
}
</script>

<template>
  <div class="error-page">
    <el-result icon="error" title="500" sub-title="抱歉，服务器开小差了，请稍后重试或联系管理员">
      <template #extra>
        <el-button type="primary" @click="router.push('/')">返回首页</el-button>
        <el-button @click="reload">刷新重试</el-button>
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
