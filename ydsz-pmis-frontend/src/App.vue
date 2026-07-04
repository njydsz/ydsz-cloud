<!--
  @file 应用根组件
  @description PMIS 前端根组件，承担 RouterView 容器职责；
               集成 Element Plus 全局配置（国际化 locale）、全局错误边界（ErrorBoundary）、
               以及应用启动时的主题初始化（从 localStorage 恢复暗黑模式）。
  @module App
-->
<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterView } from 'vue-router'
import ErrorBoundary from '@/components/common/ErrorBoundary.vue'
import { useAppStore } from '@/store/modules/app'
import i18n from '@/locales'
import { logger } from '@/utils/logger'
// Element Plus 语言包（按当前 i18n locale 动态切换）
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'

const appStore = useAppStore()

/**
 * 根据当前 vue-i18n locale 计算对应的 Element Plus 语言包
 * - zh-CN → zhCn
 * - en-US → en
 */
const elementLocale = computed(() => {
  return i18n.global.locale.value === 'en-US' ? en : zhCn
})

/**
 * 应用挂载时初始化主题
 * 从 localStorage 读取用户上次选择的主题并应用 dark class，避免刷新后主题丢失
 */
onMounted(() => {
  appStore.initTheme()
})

/**
 * ErrorBoundary 捕获顶层渲染异常时的回调
 * ErrorBoundary 内部已处理 Sentry 上报，此处仅做开发环境日志输出
 */
function onError(err: unknown, info: string) {
  logger.error('[App ErrorBoundary]', err, { info })
}
</script>

<template>
  <!-- el-config-provider 用于全局化 Element Plus 配置（国际化、尺寸、主题等） -->
  <el-config-provider :locale="elementLocale">
    <ErrorBoundary :reset-on-route-change="false" @error="onError">
      <RouterView />
    </ErrorBoundary>
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
