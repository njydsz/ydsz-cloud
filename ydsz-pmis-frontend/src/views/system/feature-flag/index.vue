<!--
  @file 特性开关管理
  @description 特性开关管理页面：提供 flag 启停与灰度发布控制台，按分类（基础设施/业务能力/界面特性/安全合规）聚合展示，灰度通过滑动条设置 0-100% 比例并立即生效；安全合规类强制开启不可关闭。对应路由 /system/feature-flag。
  @module views/system/feature-flag
-->
<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getFeatureFlagSnapshot,
  setFeatureFlagEnabled,
  setFeatureFlagRollout,
  refreshFeatureFlagCache,
} from '@/api/feature-flag'
import type { FeatureFlagSnapshot } from '@/api/feature-flag/types'

const loading = ref(false)
const snapshots = ref<FeatureFlagSnapshot[]>([])
const search = ref('')

/** 分类中文标签 */
const categoryLabel: Record<string, string> = {
  INFRASTRUCTURE: '基础设施',
  BUSINESS: '业务能力',
  UI: '界面特性',
  SAFETY: '安全合规',
}

/** 分类顺序 */
const categoryOrder = ['INFRASTRUCTURE', 'BUSINESS', 'UI', 'SAFETY']

/** 按分类聚合 */
const grouped = computed(() => {
  const groups: Record<string, FeatureFlagSnapshot[]> = {}
  for (const s of snapshots.value) {
    if (!groups[s.category]) groups[s.category] = []
    groups[s.category]!.push(s)
  }
  // 排序
  for (const k of Object.keys(groups)) {
    groups[k]!.sort((a, b) => a.key.localeCompare(b.key))
  }
  return groups
})

/** 启停数量统计 */
const stats = computed(() => {
  const total = snapshots.value.length
  const enabled = snapshots.value.filter((s) => s.effectiveValue).length
  const mandatory = snapshots.value.filter((s) => s.mandatory).length
  const inRollout = snapshots.value.filter(
    (s) => s.rolloutPercentage != null && s.rolloutPercentage > 0 && s.rolloutPercentage < 100,
  ).length
  return { total, enabled, mandatory, inRollout, disabled: total - enabled }
})

/** 过滤后 */
const filteredGrouped = computed(() => {
  if (!search.value.trim()) return grouped.value
  const kw = search.value.toLowerCase()
  const result: Record<string, FeatureFlagSnapshot[]> = {}
  for (const [cat, list] of Object.entries(grouped.value)) {
    const filtered = list.filter(
      (s) =>
        s.key.toLowerCase().includes(kw) ||
        s.description.toLowerCase().includes(kw),
    )
    if (filtered.length > 0) result[cat] = filtered
  }
  return result
})

/** 拉取特性开关快照（全量） */
async function fetchSnapshot() {
  loading.value = true
  try {
    const { data } = await getFeatureFlagSnapshot()
    snapshots.value = data
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/**
 * 切换特性开关启停状态（安全合规类强制开启不可关闭）
 * @param s 特性开关快照
 */
async function handleToggle(s: FeatureFlagSnapshot) {
  if (s.mandatory) {
    ElMessage.warning(`「${s.key}」属于安全合规类, 强制开启, 不可关闭`)
    return
  }
  const next = !s.effectiveValue
  try {
    await ElMessageBox.confirm(
      `确认将「${s.key}」${next ? '启用' : '禁用'}?`,
      '提示',
      { type: 'warning' },
    )
    const { data } = await setFeatureFlagEnabled(s.key, next)
    s.effectiveValue = data
    s.configuredValue = data
    ElMessage.success(`已${next ? '启用' : '禁用'}`)
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '操作失败')
  }
}

/**
 * 设置特性开关灰度比例并即时生效
 * @param s 特性开关快照
 * @param val 灰度比例（0-100）
 */
async function handleRolloutChange(s: FeatureFlagSnapshot, val: number) {
  try {
    const { data } = await setFeatureFlagRollout(s.key, val)
    s.rolloutPercentage = data
    ElMessage.success(`「${s.key}」灰度已设为 ${data}%`)
  } catch (e: any) {
    ElMessage.error(e?.message || '设置失败')
  }
}

/** 刷新特性开关缓存并重新拉取快照 */
async function handleRefresh() {
  try {
    await refreshFeatureFlagCache()
    await fetchSnapshot()
    ElMessage.success('缓存已刷新')
  } catch (e: any) {
    ElMessage.error(e?.message || '刷新失败')
  }
}

/**
 * 灰度比例展示文本
 * @param s 特性开关快照
 * @returns 灰度比例描述（如 '全量' / '0% (关闭)' / '85%'）
 */
function rolloutText(s: FeatureFlagSnapshot): string {
  if (s.rolloutPercentage == null) return '全量'
  if (s.rolloutPercentage === 0) return '0% (关闭)'
  if (s.rolloutPercentage === 100) return '100% (全量)'
  return `${s.rolloutPercentage}%`
}

/** 返回当前过滤结果中存在的分类（按预置顺序排列） */
function categoryOrderList(): string[] {
  return categoryOrder.filter((c) => filteredGrouped.value[c]?.length > 0)
}

onMounted(fetchSnapshot)
</script>

<template>
  <div class="feature-flag-page">
    <el-card shadow="never">
      <!-- 概览 -->
      <div class="overview">
        <div class="stat-card stat-total">
          <div class="stat-value">{{ stats.total }}</div>
          <div class="stat-label">总开关数</div>
        </div>
        <div class="stat-card stat-enabled">
          <div class="stat-value">{{ stats.enabled }}</div>
          <div class="stat-label">已启用</div>
        </div>
        <div class="stat-card stat-disabled">
          <div class="stat-value">{{ stats.disabled }}</div>
          <div class="stat-label">已禁用</div>
        </div>
        <div class="stat-card stat-mandatory">
          <div class="stat-value">{{ stats.mandatory }}</div>
          <div class="stat-label">强制开启</div>
        </div>
        <div class="stat-card stat-rollout">
          <div class="stat-value">{{ stats.inRollout }}</div>
          <div class="stat-label">灰度中</div>
        </div>
      </div>

      <!-- 工具栏 -->
      <div class="toolbar">
        <el-input
          v-model="search"
          placeholder="搜索 key 或描述"
          clearable
          style="width: 240px"
        />
        <el-button :icon="'Refresh'" @click="handleRefresh">刷新</el-button>
      </div>

      <!-- 分类展示 -->
      <el-skeleton v-if="loading" :rows="6" animated />
      <template v-else>
        <div v-for="cat in categoryOrderList()" :key="cat" class="category-block">
          <div class="category-title">
            <span class="cat-label">{{ categoryLabel[cat] }}</span>
            <el-tag size="small" type="info">{{ filteredGrouped[cat]?.length || 0 }}</el-tag>
          </div>
          <el-table :data="filteredGrouped[cat]" border stripe>
            <el-table-column prop="key" label="Key" width="240">
              <template #default="{ row }">
                <span class="key-text">{{ row.key }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
            <el-table-column label="强制" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="(row as FeatureFlagSnapshot).mandatory" size="small" type="danger">强制</el-tag>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="开关" width="100" align="center">
              <template #default="{ row }">
                <el-switch
                  :model-value="(row as FeatureFlagSnapshot).effectiveValue"
                  :disabled="(row as FeatureFlagSnapshot).mandatory"
                  inline-prompt
                  active-text="开"
                  inactive-text="关"
                  @change="() => handleToggle(row as FeatureFlagSnapshot)"
                />
              </template>
            </el-table-column>
            <el-table-column label="灰度发布" min-width="280">
              <template #default="{ row }">
                <div v-if="(row as FeatureFlagSnapshot).mandatory" class="muted">强制开启, 不支持灰度</div>
                <div v-else class="rollout-cell">
                  <el-slider
                    :model-value="(row as FeatureFlagSnapshot).rolloutPercentage ?? 100"
                    :min="0"
                    :max="100"
                    :step="5"
                    show-stops
                    style="flex: 1; margin-right: 12px"
                    @change="(v: number | number[]) => handleRolloutChange(row as FeatureFlagSnapshot, Array.isArray(v) ? v[0] : v)"
                  />
                  <span class="rollout-text">{{ rolloutText(row as FeatureFlagSnapshot) }}</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </template>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.feature-flag-page {
  .overview {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: $spacing-md;
    margin-bottom: $spacing-md;

    .stat-card {
      padding: $spacing-md;
      border-radius: 6px;
      color: #fff;
      text-align: center;
      background: $text-placeholder;
    }
    .stat-total { background: $primary-color; }
    .stat-enabled { background: $success-color; }
    .stat-disabled { background: $text-secondary; }
    .stat-mandatory { background: $danger-color; }
    .stat-rollout { background: $warning-color; }

    .stat-value {
      font-size: 28px;
      font-weight: 600;
      line-height: 1.2;
    }
    .stat-label {
      font-size: 13px;
      opacity: 0.9;
    }
  }

  .toolbar {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    margin-bottom: $spacing-md;
  }

  .category-block {
    margin-bottom: $spacing-lg;

    .category-title {
      display: flex;
      align-items: center;
      gap: $spacing-sm;
      margin-bottom: $spacing-sm;
      padding-bottom: $spacing-sm;
      border-bottom: 1px solid $border-extra-light;

      .cat-label {
        font-size: 15px;
        font-weight: 600;
        color: $text-primary;
      }
    }
  }

  .key-text {
    font-family: 'SFMono-Regular', Consolas, monospace;
    color: $primary-color;
    font-size: 13px;
  }

  .muted {
    color: $text-placeholder;
  }

  .rollout-cell {
    display: flex;
    align-items: center;
    width: 100%;

    .rollout-text {
      width: 90px;
      font-size: 12px;
      color: $text-secondary;
      font-family: 'SFMono-Regular', Consolas, monospace;
    }
  }
}
</style>
