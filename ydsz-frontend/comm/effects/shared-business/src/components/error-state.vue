<!--
 * error-state 通用组件 — 统一错误状态展示
 *
 * @path comm\effects\shared-business\src\components\error-state.vue
 * @author ydsz-team
 * @since 1.1.0
-->
<script lang="ts" setup>
/**
 * 统一错误状态组件 — 展示错误信息 + 重试/返回操作
 *
 * 兼容 common-ui 中既有 error-state 的场景，这里提供更完整的语义化 API：
 * - retry 回调触发重试
 * - 可选返回上一页
 */
import { ElButton } from 'element-plus';

interface Props {
  /** 错误标题 */
  title?: string;
  /** 错误详情 */
  description?: string;
  /** 是否显示重试按钮 */
  showRetry?: boolean;
  /** 是否显示返回按钮 */
  showBack?: boolean;
  /** 错误类型（决定图标色系） */
  type?: 'error' | 'warning' | 'info';
}

withDefaults(defineProps<Props>(), {
  title: '页面出错了',
  description: '请求失败，请稍后重试',
  showRetry: true,
  showBack: false,
  type: 'error',
});

const emit = defineEmits<{
  retry: [];
  back: [];
}>();

function handleBack() {
  if (window.history.length > 1) {
    window.history.back();
  }
  emit('back');
}
</script>

<template>
  <div class="error-state" :class="`error-state--${type}`">
    <div class="error-state__icon">!</div>
    <h3 class="error-state__title">{{ title }}</h3>
    <p v-if="description" class="error-state__desc">{{ description }}</p>
    <div class="error-state__actions">
      <el-button v-if="showRetry" type="primary" size="small" @click="emit('retry')">
        重试
      </el-button>
      <el-button v-if="showBack" size="small" @click="handleBack">
        返回
      </el-button>
    </div>
    <div v-if="$slots.default" class="error-state__extra">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 16px;
  text-align: center;
}
.error-state__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #fef0f0;
  color: #f56c6c;
  font-size: 30px;
  font-weight: 700;
  margin-bottom: 16px;
}
.error-state--warning .error-state__icon {
  background: #fdf6ec;
  color: #e6a23c;
}
.error-state--info .error-state__icon {
  background: #f4f4f5;
  color: #909399;
}
.error-state__title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px;
}
.error-state__desc {
  font-size: 13px;
  color: #909399;
  margin: 0 0 16px;
  max-width: 320px;
}
.error-state__actions {
  display: flex;
  gap: 8px;
}
.error-state__extra {
  margin-top: 12px;
}
</style>
