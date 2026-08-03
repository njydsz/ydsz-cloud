<!--
 表单骨架屏组件

 适用于表单页面的加载骨架屏，包含表单项和提交按钮。

 @path main/src/views/_core/subapp/skeletons/FormSkeleton.vue
 @author ydsz-team
 @since 1.0.0
-->
<template>
  <div class="page-skeleton form-skeleton">
    <div class="skeleton-form">
      <div class="skeleton-form-item" v-for="i in 4" :key="i">
        <div class="skeleton-label"></div>
        <div class="skeleton-input"></div>
      </div>
      <div class="skeleton-actions">
        <div class="skeleton-button"></div>
        <div class="skeleton-button primary"></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-skeleton {
  padding: 24px;
  animation: fadeIn 0.3s ease-in;
}

.skeleton-form {
  max-width: 600px;
  margin: 0 auto;
}

.skeleton-form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 24px;
}

.skeleton-label {
  height: 14px;
  width: 80px;
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

.skeleton-form-item:nth-child(1) .skeleton-label {
  animation-delay: 0.2s;
}

.skeleton-form-item:nth-child(2) .skeleton-label {
  animation-delay: 0.3s;
}

.skeleton-form-item:nth-child(3) .skeleton-label {
  animation-delay: 0.4s;
}

.skeleton-form-item:nth-child(4) .skeleton-label {
  animation-delay: 0.5s;
}

.skeleton-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 32px;
}

.skeleton-button {
  width: 80px;
  height: 32px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
  animation-delay: 0.6s;
}

.skeleton-button.primary {
  width: 100px;
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