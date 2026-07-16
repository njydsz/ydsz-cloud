<!--
  @fileoverview 通用卡片骨架屏组件
  @description 仪表盘 / 卡片列表首次加载时以骨架卡片占位：
  - Props: count(骨架卡片数量)
  - 按 count 等分宽度并排展示骨架卡片
  - 场景: 首屏占位、骨架屏
  @module components/common/SkeletonCard
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * 卡片骨架屏
 *
 * 适用于仪表盘指标卡、卡片列表等场景的首屏占位，
 * 按 count 等分宽度并排展示骨架卡片。
 *
 * 使用示例：
 *   <SkeletonCard :count="4" />
 */
withDefaults(
  defineProps<{
    /** 骨架卡片数量（建议为 24 的约数：2/3/4/6/8/12） */
    count?: number
  }>(),
  {
    count: 4,
  },
)
</script>

<template>
  <el-row :gutter="16">
    <el-col v-for="i in count" :key="i" :span="24 / count">
      <el-card shadow="hover">
        <el-skeleton :rows="4" animated />
      </el-card>
    </el-col>
  </el-row>
</template>
