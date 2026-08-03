<!--
 详情骨架屏组件

 适用于详情页面的加载骨架屏，包含标签 - 值对的结构。

 @path main/src/views/_core/subapp/skeletons/DetailSkeleton.vue
 @author ydsz-team
 @since 1.0.0
-->
<template>
  <div class="page-skeleton detail-skeleton">
    <div class="skeleton-detail">
      <div class="skeleton-detail-item" v-for="i in 6" :key="i">
        <div class="skeleton-label"></div>
        <div class="skeleton-value"></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-skeleton {
  padding: 24px;
  animation: fadeIn 0.3s ease-in;
}

.skeleton-detail {
  max-width: 800px;
  margin: 0 auto;
}

.skeleton-detail-item {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
  padding: 12px;
  background: #fafafa;
  border-radius: 8px;
}

.skeleton-label {
  width: 120px;
  height: 16px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
  flex-shrink: 0;
}

.skeleton-value {
  flex: 1;
  height: 16px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
  animation-delay: 0.1s;
}

.skeleton-detail-item:nth-child(1) .skeleton-label {
  animation-delay: 0.2s;
}

.skeleton-detail-item:nth-child(2) .skeleton-label {
  animation-delay: 0.3s;
}

.skeleton-detail-item:nth-child(3) .skeleton-label {
  animation-delay: 0.4s;
}

.skeleton-detail-item:nth-child(4) .skeleton-label {
  animation-delay: 0.5s;
}

.skeleton-detail-item:nth-child(5) .skeleton-label {
  animation-delay: 0.6s;
}

.skeleton-detail-item:nth-child(6) .skeleton-label {
  animation-delay: 0.7s;
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