<!--
  @fileoverview 通用详情骨架屏组件
  @description 详情页首次加载时以骨架占位替代空白表单：
  - Props: rows(骨架文本行数)
  - 包含头部圆形占位 + 标题 + 多行文本
  - 场景: 详情页/表单页首屏占位
  @module components/common/SkeletonDetail
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * 详情骨架屏
 *
 * 适用于详情页 / 表单页首次加载占位，包含头部圆形占位 + 标题 + 多行文本。
 *
 * 使用示例：
 *   <SkeletonDetail :rows="8" />
 */
withDefaults(
  defineProps<{
    /** 骨架文本行数 */
    rows?: number
  }>(),
  {
    rows: 8,
  },
)
</script>

<template>
  <el-card>
    <el-skeleton :rows="rows" animated>
      <template #template>
        <div style="display: flex; align-items: center; margin-bottom: 16px">
          <el-skeleton-item variant="circle" style="width: 40px; height: 40px; margin-right: 12px" />
          <el-skeleton-item variant="h3" style="width: 30%" />
        </div>
        <el-skeleton-item v-for="i in rows" :key="i" variant="text" style="margin-bottom: 12px; width: 100%" />
      </template>
    </el-skeleton>
  </el-card>
</template>
