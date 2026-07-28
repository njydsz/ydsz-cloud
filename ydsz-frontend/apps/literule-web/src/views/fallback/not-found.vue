<!--
 * not-found 页面组件
 *
 * @path apps\literule-web\src\views\fallback\not-found.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 404 兜底页
 * <p>Vue Router 中未匹配到任何路由时显示的 404 兜底页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { useRouter } from 'vue-router';

const router = useRouter();
</script>

<template>
  <div class="flex h-full min-h-[400px] items-center justify-center">
    <div class="text-center">
      <div class="mb-4 text-6xl font-bold text-gray-300">404</div>
      <div class="mb-6 text-gray-500">页面不存在</div>
      <ElButton type="primary" @click="router.back()">返回上一页</ElButton>
    </div>
  </div>
</template>
