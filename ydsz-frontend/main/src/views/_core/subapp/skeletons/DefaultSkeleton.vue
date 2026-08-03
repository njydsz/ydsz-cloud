<!--
 * 默认骨架屏组件
 *
 * 通用的页面加载骨架屏，包含标题和段落结构。
 *
 * @path main/src/views/_core/subapp/skeletons/DefaultSkeleton.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<template>
  <div class="page-skeleton default-skeleton">
    <div class="skeleton-header">
      <div class="skeleton-avatar"></div>
      <div class="skeleton-title"></div>
    </div>
    <div class="skeleton-content">
      <div class="skeleton-paragraph"></div>
      <div class="skeleton-paragraph short"></div>
      <div class="skeleton-paragraph"></div>
    </div>
  </div>
</template>

<style scoped>
.page-skeleton {
  padding: 24px;
  max-width: 600px;
  margin: 0 auto;
  animation: fadeIn 0.3s ease-in;
}

.skeleton-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.skeleton-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
}

.skeleton-title {
  flex: 1;
  height: 20px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
  animation-delay: 0.1s;
}

.skeleton-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.skeleton-paragraph {
  height: 16px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
}

.skeleton-paragraph.short {
  width: 60%;
}

.skeleton-paragraph:nth-child(1) {
  animation-delay: 0.2s;
}

.skeleton-paragraph:nth-child(2) {
  animation-delay: 0.3s;
}

.skeleton-paragraph:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes skeleton-loading {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}
</style>
