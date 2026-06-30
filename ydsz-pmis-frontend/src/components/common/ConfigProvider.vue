<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

const locale = ref(zhCn)
const size = computed(() => 'default')
</script>

<template>
  <el-config-provider :locale="locale" :size="size">
    <slot />
  </el-config-provider>
</template>
