<!--
  @file 系统变更日志
  @description 展示系统版本变更日志，按版本号分组展示，支持按类型和分类筛选。
  @module views/system/changelog
-->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import { getChangelog, type ChangelogEntry, type ChangelogType, type ChangelogCategory } from '@/api/system/changelog'
import { isHandledError } from '@/utils/error'

const { t } = useI18n()

const loading = ref(false)
const entries = ref<ChangelogEntry[]>([])

// 筛选
const filterType = ref<string>('')
const filterCategory = ref<string>('')

// 类型配置
const typeConfig: Record<ChangelogType, { label: string; color: string; icon: string }> = {
  FEATURE:     { label: '新功能',  color: '#67C23A', icon: 'Star' },
  IMPROVEMENT: { label: '优化',    color: '#409EFF', icon: 'MagicStick' },
  BUGFIX:      { label: '修复',    color: '#E6A23C', icon: 'CircleCheck' },
  SECURITY:    { label: '安全',    color: '#F56C6C', icon: 'Lock' },
}

// 分类配置
const categoryConfig: Record<ChangelogCategory, { label: string; icon: string }> = {
  frontend: { label: '前端',  icon: 'Monitor' },
  backend:  { label: '后端',  icon: 'Cpu' },
  infra:    { label: '基础设施', icon: 'SetUp' },
  security: { label: '安全',  icon: 'Lock' },
}

// 按版本分组
const groupedByVersion = computed(() => {
  const filtered = entries.value.filter(e => {
    if (filterType.value && e.type !== filterType.value) return false
    if (filterCategory.value && e.category !== filterCategory.value) return false
    return true
  })
  const groups: Record<string, ChangelogEntry[]> = {}
  for (const e of filtered) {
    if (!groups[e.version]) groups[e.version] = []
    groups[e.version].push(e)
  }
  return groups
})

// 版本列表（倒序）
const versionList = computed(() => Object.keys(groupedByVersion.value).sort((a, b) => b.localeCompare(a, undefined, { numeric: true })))

// 格式化日期
function formatDate(date: string): string {
  if (!date) return '-'
  return date.replace(/-/g, '/')
}

async function loadData() {
  loading.value = true
  try {
    const { data } = await getChangelog()
    entries.value = data || []
  } catch (e) {
    if (!isHandledError(e)) {
      ElMessage.error('加载变更日志失败')
    }
    entries.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <PageLayout>
    <template #header>
      <div class="flex items-center justify-between">
        <h2 class="text-lg font-semibold">{{ t('common.changelog') }}</h2>
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>
          {{ t('common.refresh') }}
        </el-button>
      </div>
    </template>

    <!-- 筛选 -->
    <div class="mb-4 flex gap-3">
      <el-select v-model="filterType" placeholder="全部类型" clearable style="width: 140px">
        <el-option v-for="(v, k) in typeConfig" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-select v-model="filterCategory" placeholder="全部分类" clearable style="width: 140px">
        <el-option v-for="(v, k) in categoryConfig" :key="k" :label="v.label" :value="k" />
      </el-select>
    </div>

    <!-- 时间线 -->
    <div v-loading="loading" class="changelog-container">
      <template v-if="versionList.length > 0">
        <div v-for="version in versionList" :key="version" class="version-block">
          <!-- 版本头 -->
          <div class="version-header">
            <el-tag type="primary" size="large" effect="dark">{{ version }}</el-tag>
            <span class="version-date">{{ formatDate(groupedByVersion[version][0]?.releaseDate || '') }}</span>
          </div>

          <!-- 变更条目 -->
          <el-timeline class="version-timeline">
            <el-timeline-item
              v-for="(entry, idx) in groupedByVersion[version]"
              :key="idx"
              :timestamp="formatDate(entry.releaseDate)"
              placement="top"
              :color="typeConfig[entry.type]?.color || '#909399'"
            >
              <el-card shadow="hover" class="entry-card">
                <div class="entry-header">
                  <el-tag
                    :color="typeConfig[entry.type]?.color"
                    effect="light"
                    size="small"
                    style="color: var(--el-text-color-primary); border: 1px solid var(--el-border-color-light)"
                  >
                    {{ typeConfig[entry.type]?.label || entry.type }}
                  </el-tag>
                  <el-tag size="small" type="info">
                    {{ categoryConfig[entry.category]?.label || entry.category }}
                  </el-tag>
                </div>
                <h4 class="entry-title">{{ entry.title }}</h4>
                <p class="entry-desc">{{ entry.description }}</p>
              </el-card>
            </el-timeline-item>
          </el-timeline>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="暂无变更日志" />
    </div>
  </PageLayout>
</template>

<style lang="scss" scoped>
.changelog-container {
  min-height: 300px;
}

.version-block {
  margin-bottom: 32px;
}

.version-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;

  .version-date {
    color: var(--el-text-secondary);
    font-size: 14px;
  }
}

.version-timeline {
  padding-left: 8px;
}

.entry-card {
  .entry-header {
    display: flex;
    gap: 8px;
    margin-bottom: 8px;
  }

  .entry-title {
    font-size: 15px;
    font-weight: 600;
    margin: 0 0 6px 0;
    color: var(--el-text-color-primary);
  }

  .entry-desc {
    font-size: 13px;
    color: var(--el-text-secondary);
    margin: 0;
    line-height: 1.6;
  }
}
</style>
