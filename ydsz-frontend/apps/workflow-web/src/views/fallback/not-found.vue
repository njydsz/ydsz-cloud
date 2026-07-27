<script lang="ts" setup>
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
