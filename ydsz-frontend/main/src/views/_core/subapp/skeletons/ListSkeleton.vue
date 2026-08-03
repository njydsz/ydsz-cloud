<!--
 列表骨架屏组件

 适用于列表页面的加载骨架屏，包含搜索栏和表格结构。

 @path main/src/views/_core/subapp/skeletons/ListSkeleton.vue
 @author ydsz-team
 @since 1.0.0
-->
<template>
  <div class="page-skeleton list-skeleton">
    <!-- 搜索栏骨架 -->
    <div class="skeleton-search-bar">
      <div class="skeleton-search-item" v-for="i in 3" :key="i">
        <div class="skeleton-label"></div>
        <div class="skeleton-input"></div>
      </div>
      <div class="skeleton-button"></div>
    </div>

    <!-- 表格骨架 -->
    <div class="skeleton-table">
      <div class="skeleton-table-header">
        <div class="skeleton-table-cell" v-for="i in 5" :key="i"></div>
      </div>
      <div class="skeleton-table-body">
        <div class="skeleton-table-row" v-for="i in 8" :key="i">
          <div class="skeleton-table-cell" v-for="j in 5" :key="j"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-skeleton {
  padding: 24px;
  animation: fadeIn 0.3s ease-in;
}

.skeleton-search-bar {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
}

.skeleton-search-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.skeleton-label {
  height: 14px;
  width: 60px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
}

.skeleton-input {
  height: 32px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
  animation-delay: 0.1s;
}

.skeleton-button {
  width: 80px;
  height: 32px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
  animation-delay: 0.2s;
  align-self: flex-end;
}

.skeleton-table {
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  overflow: hidden;
}

.skeleton-table-header {
  display: flex;
  background: #fafafa;
  border-bottom: 1px solid #e8e8e8;
}

.skeleton-table-cell {
  flex: 1;
  height: 40px;
  padding: 12px;
  border-right: 1px solid #e8e8e8;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
}

.skeleton-table-cell:last-child {
  border-right: none;
}

.skeleton-table-body {
  background: #fff;
}

.skeleton-table-row {
  display: flex;
  border-bottom: 1px solid #e8e8e8;
}

.skeleton-table-row:last-child {
  border-bottom: none;
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