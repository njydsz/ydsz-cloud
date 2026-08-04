<!--
 * empty-state 通用组件
 *
 * @path comm\effects\shared-business\src\components\empty-state.vue
 * @author ydsz-team
 * @since 1.1.0
-->
<script lang="ts" setup>
/**
 * 统一空状态组件 — 替代散落的「暂无数据」文案
 */
interface Props {
  /** 空状态文案 */
  text?: string;
  /** 展示插图区域（slot） */
  showIcon?: boolean;
}

withDefaults(defineProps<Props>(), {
  text: '暂无数据',
  showIcon: true,
});
</script>

<template>
  <div class="empty-state">
    <svg
      v-if="showIcon"
      class="empty-state__icon"
      viewBox="0 0 64 64"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
    >
      <rect x="12" y="20" width="40" height="30" rx="4" fill="#e4e7ed" />
      <line x1="20" y1="30" x2="44" y2="30" stroke="#a8abb2" stroke-width="2" stroke-linecap="round" />
      <line x1="20" y1="38" x2="38" y2="38" stroke="#a8abb2" stroke-width="2" stroke-linecap="round" />
    </svg>
    <p class="empty-state__text">{{ text }}</p>
    <div v-if="$slots.default" class="empty-state__action">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 16px;
  color: #909399;
}
.empty-state__icon {
  width: 64px;
  height: 64px;
  margin-bottom: 12px;
}
.empty-state__text {
  font-size: 14px;
  line-height: 1.5;
}
.empty-state__action {
  margin-top: 12px;
}
</style>
