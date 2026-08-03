<!--
 仪表盘骨架屏组件

 适用于仪表盘页面的加载骨架屏，包含统计卡片和图表区域。

 @path main/src/views/_core/subapp/skeletons/DashboardSkeleton.vue
 @author ydsz-team
 @since 1.0.0
-->
<template>
  <div class="page-skeleton dashboard-skeleton">
    <!-- 统计卡片骨架 -->
    <div class="skeleton-stats">
      <div class="skeleton-stat-card" v-for="i in 4" :key="i">
        <div class="skeleton-stat-title"></div>
        <div class="skeleton-stat-value"></div>
        <div class="skeleton-stat-trend"></div>
      </div>
    </div>

    <!-- 图表骨架 -->
    <div class="skeleton-charts">
      <div class="skeleton-chart-card" v-for="i in 2" :key="i">
        <div class="skeleton-chart-title"></div>
        <div class="skeleton-chart-content"></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-skeleton {
  padding: 24px;
  animation: fadeIn 0.3s ease-in;
}

.skeleton-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.skeleton-stat-card {
  padding: 20px;
  background: #fafafa;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.skeleton-stat-title {
  height: 14px;
  width: 80px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
}

.skeleton-stat-value {
  height: 32px;
  width: 120px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
  animation-delay: 0.1s;
}

.skeleton-stat-trend {
  height: 20px;
  width: 60px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
  animation-delay: 0.2s;
}

.skeleton-stat-card:nth-child(1) .skeleton-stat-title {
  animation-delay: 0.3s;
}

.skeleton-stat-card:nth-child(2) .skeleton-stat-title {
  animation-delay: 0.4s;
}

.skeleton-stat-card:nth-child(3) .skeleton-stat-title {
  animation-delay: 0.5s;
}

.skeleton-stat-card:nth-child(4) .skeleton-stat-title {
  animation-delay: 0.6s;
}

.skeleton-charts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 16px;
}

.skeleton-chart-card {
  padding: 20px;
  background: #fafafa;
  border-radius: 8px;
}

.skeleton-chart-title {
  height: 18px;
  width: 100px;
  margin-bottom: 16px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
  animation-delay: 0.7s;
}

.skeleton-chart-content {
  height: 300px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
  animation-delay: 0.8s;
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