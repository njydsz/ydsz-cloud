<!--
  @file 特性开关管理
  @description 特性开关管理页面：提供 flag 启停与灰度发布控制台，按分类（基础设施/业务能力/界面特性/安全合规）聚合展示，灰度通过滑动条设置 0-100% 比例并立即生效；安全合规类强制开启不可关闭。对应路由 /system/feature-flag。
  @module views/system/feature-flag
-->
<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getFeatureFlagSnapshot,
  setFeatureFlagEnabled,
  setFeatureFlagRollout,
  refreshFeatureFlagCache,
} from '@/api/feature-flag'
import type { FeatureFlagSnapshot } from '@/api/feature-flag/types'

const { t } = useI18n()

const loading = ref(false)
const snapshots = ref<FeatureFlagSnapshot[]>([])
const search = ref('')

/** 分类中文标签 */
const categoryLabel = computed<Record<string, string>>(() => ({
  INFRASTRUCTURE: t('system.featureFlag.category.INFRASTRUCTURE'),
  BUSINESS: t('system.featureFlag.category.BUSINESS'),
  UI: t('system.featureFlag.category.UI'),
  SAFETY: t('system.featureFlag.category.SAFETY'),
}))

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
    (s) => s.rolloutPercentage !== null && s.rolloutPercentage !== undefined && s.rolloutPercentage > 0 && s.rolloutPercentage < 100,
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
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || t('system.featureFlag.messages.loadFailed'))
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
    ElMessage.warning(t('system.featureFlag.messages.mandatoryTip', { key: s.key }))
    return
  }
  const next = !s.effectiveValue
  try {
    await ElMessageBox.confirm(
      t('system.featureFlag.messages.confirmToggle', {
        key: s.key,
        action: next ? t('system.featureFlag.messages.toggleActionEnable') : t('system.featureFlag.messages.toggleActionDisable'),
      }),
      t('common.tip'),
      { type: 'warning' },
    )
    const { data } = await setFeatureFlagEnabled(s.key, next)
    s.effectiveValue = data
    s.configuredValue = data
    ElMessage.success(t('system.featureFlag.messages.toggled', {
      action: next ? t('system.featureFlag.messages.toggleActionEnable') : t('system.featureFlag.messages.toggleActionDisable'),
    }))
  } catch (e: unknown) {
    if (e !== 'cancel') ElMessage.error((e as Error)?.message || t('system.featureFlag.messages.operationFailed'))
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
    ElMessage.success(t('system.featureFlag.messages.rolloutSet', { key: s.key, percent: data }))
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || t('system.featureFlag.messages.rolloutSetFailed'))
  }
}

/** 刷新特性开关缓存并重新拉取快照 */
async function handleRefresh() {
  try {
    await refreshFeatureFlagCache()
    await fetchSnapshot()
    ElMessage.success(t('system.featureFlag.messages.cacheRefreshed'))
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || t('system.featureFlag.messages.refreshFailed'))
  }
}

/**
 * 灰度比例展示文本
 * @param s 特性开关快照
 * @returns 灰度比例描述（如 '全量' / '0% (关闭)' / '85%'）
 */
function rolloutText(s: FeatureFlagSnapshot): string {
  if (s.rolloutPercentage === null || s.rolloutPercentage === undefined) return t('system.featureFlag.rolloutText.full')
  if (s.rolloutPercentage === 0) return t('system.featureFlag.rolloutText.closed')
  if (s.rolloutPercentage === 100) return t('system.featureFlag.rolloutText.full100')
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
          <div class="stat-label">{{ t('system.featureFlag.stats.total') }}</div>
        </div>
        <div class="stat-card stat-enabled">
          <div class="stat-value">{{ stats.enabled }}</div>
          <div class="stat-label">{{ t('system.featureFlag.stats.enabled') }}</div>
        </div>
        <div class="stat-card stat-disabled">
          <div class="stat-value">{{ stats.disabled }}</div>
          <div class="stat-label">{{ t('system.featureFlag.stats.disabled') }}</div>
        </div>
        <div class="stat-card stat-mandatory">
          <div class="stat-value">{{ stats.mandatory }}</div>
          <div class="stat-label">{{ t('system.featureFlag.stats.mandatory') }}</div>
        </div>
        <div class="stat-card stat-rollout">
          <div class="stat-value">{{ stats.inRollout }}</div>
          <div class="stat-label">{{ t('system.featureFlag.stats.inRollout') }}</div>
        </div>
      </div>

      <!-- 工具栏 -->
      <div class="toolbar">
        <el-input
          v-model="search"
          :placeholder="t('system.featureFlag.search.placeholder')"
          clearable
          style="width: 240px"
        />
        <el-button :icon="'Refresh'" @click="handleRefresh">{{ t('system.featureFlag.buttons.refresh') }}</el-button>
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
            <el-table-column prop="key" :label="t('system.featureFlag.columns.key')" width="240">
              <template #default="{ row }">
                <span class="key-text">{{ row.key }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="description" :label="t('system.featureFlag.columns.description')" min-width="200" show-overflow-tooltip />
            <el-table-column :label="t('system.featureFlag.columns.mandatory')" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="(row as FeatureFlagSnapshot).mandatory" size="small" type="danger">{{ t('system.featureFlag.columns.mandatory') }}</el-tag>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('system.featureFlag.columns.switch')" width="100" align="center">
              <template #default="{ row }">
                <el-switch
                  :model-value="(row as FeatureFlagSnapshot).effectiveValue"
                  :disabled="(row as FeatureFlagSnapshot).mandatory"
                  inline-prompt
                  :active-text="t('system.featureFlag.switch.on')"
                  :inactive-text="t('system.featureFlag.switch.off')"
                  @change="() => handleToggle(row as FeatureFlagSnapshot)"
                />
              </template>
            </el-table-column>
            <el-table-column :label="t('system.featureFlag.columns.rollout')" min-width="280">
              <template #default="{ row }">
                <div v-if="(row as FeatureFlagSnapshot).mandatory" class="muted">{{ t('system.featureFlag.rolloutMandatoryHint') }}</div>
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
