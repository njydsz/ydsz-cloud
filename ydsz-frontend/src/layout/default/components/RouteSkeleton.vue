<!--
  @fileoverview 路由骨架屏
  @description 路由懒加载时的骨架屏占位组件：
  - 模拟典型列表页布局：标题栏 + 筛选栏 + 表格行
  - 避免白屏闪烁，提升用户感知性能
  - 配合 <Suspense> 的 #fallback 插槽使用
  @module layout/default/components/RouteSkeleton
  @author ydsz-team
  @since 1.0.0
-->
<template>
  <div class="route-skeleton" role="status" :aria-label="$t('common.pageLoading')">
    <!-- 标题栏占位 -->
    <div class="skeleton-header">
      <div class="skeleton-bar skeleton-title" />
      <div class="skeleton-bar skeleton-action" />
    </div>
    <!-- 筛选栏占位 -->
    <div class="skeleton-filter">
      <div class="skeleton-bar skeleton-filter-item" v-for="i in 3" :key="i" />
      <div class="skeleton-bar skeleton-filter-btn" />
    </div>
    <!-- 表格行占位 -->
    <div class="skeleton-table">
      <div class="skeleton-row" v-for="i in 8" :key="i">
        <div class="skeleton-bar skeleton-cell" v-for="j in 5" :key="j" />
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.route-skeleton {
  padding: $spacing-md;
}

/* 骨架条基础样式 + 闪烁动画 */
.skeleton-bar {
  display: inline-block;
  background: linear-gradient(
    90deg,
    #{$border-lighter} 25%,
    #{$border-extra-light} 37%,
    #{$border-lighter} 63%
  );
  background-size: 400% 100%;
  border-radius: $border-radius-base;
  animation: skeleton-shimmer 1.4s ease infinite;
}

@keyframes skeleton-shimmer {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}

.skeleton-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-md;

  .skeleton-title {
    width: 180px;
    height: 24px;
  }

  .skeleton-action {
    width: 80px;
    height: 32px;
  }
}

.skeleton-filter {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-md;
  padding: $spacing-md;
  background: $bg-white;
  border-radius: $border-radius-base;

  .skeleton-filter-item {
    width: 200px;
    height: 32px;
  }

  .skeleton-filter-btn {
    width: 64px;
    height: 32px;
  }
}

.skeleton-table {
  background: $bg-white;
  border-radius: $border-radius-base;
  padding: $spacing-sm $spacing-md;

  .skeleton-row {
    display: flex;
    align-items: center;
    gap: $spacing-md;
    padding: $spacing-sm 0;
    border-bottom: 1px solid $border-extra-light;

    &:last-child {
      border-bottom: none;
    }
  }

  .skeleton-cell {
    flex: 1;
    height: 16px;

    /* 第 1 列窄一点模拟序号/复选框 */
    &:first-child {
      flex: 0 0 48px;
    }
    /* 第 2 列宽一点模拟名称 */
    &:nth-child(2) {
      flex: 0 0 180px;
    }
  }
}

/* 尊重用户减少动画偏好 */
@media (prefers-reduced-motion: reduce) {
  .skeleton-bar {
    animation: none;
  }
}
</style>
