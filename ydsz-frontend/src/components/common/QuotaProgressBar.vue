<!--
  @fileoverview 存储配额进度条组件
  @description 展示存储容量与文件数量的使用情况，基于 el-progress 双进度条。
  - Props: quota（StorageQuotaVO 配额信息）
  @module components/common/QuotaProgressBar
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * 存储配额进度条组件
 *
 * 展示存储容量与文件数量的使用情况，基于 el-progress 双进度条，
 * 根据使用率自动切换进度条颜色。
 */
import { computed } from 'vue'
import type { StorageQuotaVO } from '@/api/nextwiki/types'

const props = defineProps<{
  /** 配额信息（含存储限额与文件数限额） */
  quota: StorageQuotaVO | null
}>()

/** 存储使用百分比 */
const storagePercent = computed(() => {
  if (!props.quota || props.quota.quotaLimit === 0) return 0
  return Math.round((props.quota.quotaUsed / props.quota.quotaLimit) * 100)
})

/** 文件数使用百分比 */
const fileCountPercent = computed(() => {
  if (!props.quota || props.quota.fileCountLimit === 0) return 0
  return Math.round((props.quota.fileCountUsed / props.quota.fileCountLimit) * 100)
})

/**
 * 根据使用率返回进度条颜色
 * >= 90% 红色，>= 70% 橙色，< 70% 绿色
 */
function progressColor(percent: number): string {
  if (percent >= 90) return '#F56C6C'
  if (percent >= 70) return '#E6A23C'
  return '#67C23A'
}

/** 将字节数格式化为可读的文件大小字符串 */
function formatSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${units[i]}`
}
</script>

<template>
  <div v-if="quota" class="quota-progress">
    <div class="quota-item">
      <div class="quota-item__header">
        <span class="quota-item__label">{{ $t('nextwiki.quota.total') }}</span>
        <span class="quota-item__value">
          {{ formatSize(quota.quotaUsed) }} / {{ formatSize(quota.quotaLimit) }}
        </span>
      </div>
      <el-progress
        :percentage="storagePercent"
        :color="progressColor(storagePercent)"
        :stroke-width="16"
        :text-inside="true"
      />
    </div>
    <div class="quota-item">
      <div class="quota-item__header">
        <span class="quota-item__label">{{ $t('nextwiki.quota.fileCount') }}</span>
        <span class="quota-item__value">
          {{ quota.fileCountUsed }} / {{ quota.fileCountLimit }}
        </span>
      </div>
      <el-progress
        :percentage="fileCountPercent"
        :color="progressColor(fileCountPercent)"
        :stroke-width="16"
        :text-inside="true"
      />
    </div>
  </div>
  <el-empty v-else :description="$t('nextwiki.quota.title')" />
</template>

<style scoped>
.quota-progress {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.quota-item__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.quota-item__label {
  font-weight: 600;
  font-size: 14px;
}
.quota-item__value {
  color: #909399;
  font-size: 13px;
}
</style>
