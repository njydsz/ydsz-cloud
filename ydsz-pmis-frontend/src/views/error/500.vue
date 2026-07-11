<!--
  @file 500 错误页
  @description 服务端异常或路由懒加载失败（chunk load error）时展示的兜底错误页，
               提供返回首页与刷新重试入口；由 router.onError 跳转或用户手动访问 /500。
  @module views/error/500
-->
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

const router = useRouter()
const { t } = useI18n()

/** 刷新当前页：通过 location.reload 触发整页重载，规避 chunk 缓存问题 */
function reload() {
  window.location.reload()
}
</script>

<template>
  <div class="error-page">
    <el-result icon="error" title="500" :sub-title="t('common.serverErrorSubtitle')">
      <template #extra>
        <el-button type="primary" @click="router.push('/')">{{ t('common.backHome') }}</el-button>
        <el-button @click="reload">{{ t('common.refreshRetry') }}</el-button>
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
