<!--
  @file Element Plus 全局配置提供者
  @description 统一注入 Element Plus 的国际化语言包与组件默认尺寸，供 App.vue 或子树使用
  @module components/common/ConfigProvider
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

/** Element Plus 中文语言包 */
const locale = ref(zhCn)
/** 组件全局尺寸 */
const size = computed<'default' | 'small' | 'large'>(() => 'default')
</script>

<template>
  <el-config-provider :locale="locale" :size="size">
    <slot />
  </el-config-provider>
</template>
