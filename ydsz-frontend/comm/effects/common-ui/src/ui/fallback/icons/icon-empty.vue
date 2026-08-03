<!--
 * 空状态图标组件
 *
 * @path comm\effects\common-ui\src\ui\fallback\icons\icon-empty.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
defineOptions({
  name: 'IconEmpty',
});
</script>

<template>
  <svg
    viewBox="0 0 200 200"
    xmlns="http://www.w3.org/2000/svg"
    class="empty-state-icon"
    role="img"
    aria-label="暂无数据"
  >
    <title>暂无数据</title>
    <desc>空状态图标，表示当前没有可显示的数据内容</desc>
    <!-- 背景圆形 -->
    <circle cx="100" cy="100" r="90" fill="#f5f7fa" />
    
    <!-- 盒子主体 -->
    <g transform="translate(60, 70)">
      <!-- 盒子底部 -->
      <path
        d="M 10 40 L 40 55 L 70 40 L 70 70 L 40 85 L 10 70 Z"
        fill="#e4e7ed"
        stroke="#c0c4cc"
        stroke-width="2"
      />
      
      <!-- 盒子左侧 -->
      <path
        d="M 10 40 L 40 55 L 40 85 L 10 70 Z"
        fill="#dcdfe6"
        stroke="#c0c4cc"
        stroke-width="2"
      />
      
      <!-- 盒子右侧 -->
      <path
        d="M 70 40 L 40 55 L 40 85 L 70 70 Z"
        fill="#e4e7ed"
        stroke="#c0c4cc"
        stroke-width="2"
      />
      
      <!-- 盒子顶部开口 -->
      <ellipse
        cx="40"
        cy="40"
        rx="30"
        ry="15"
        fill="#f5f7fa"
        stroke="#c0c4cc"
        stroke-width="2"
      />
      
      <!-- 盒子顶部开口内部阴影 -->
      <ellipse
        cx="40"
        cy="42"
        rx="28"
        ry="13"
        fill="#ebeef5"
      />
    </g>
    
    <!-- 装饰性小圆点 -->
    <circle cx="50" cy="50" r="3" fill="#c0c4cc" opacity="0.5" />
    <circle cx="150" cy="60" r="2" fill="#c0c4cc" opacity="0.5" />
    <circle cx="140" cy="150" r="2.5" fill="#c0c4cc" opacity="0.5" />
    
    <!-- 文字提示 -->
    <text
      x="100"
      y="170"
      text-anchor="middle"
      font-size="14"
      fill="#909399"
      font-family="system-ui, -apple-system, sans-serif"
    >
      暂无数据
    </text>
  </svg>
</template>

<style scoped>
.empty-state-icon {
  width: 100%;
  height: 100%;
}
</style>
