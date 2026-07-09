<!--
  @file 规则集市场
  @description 列出市场中的全部规则集（RulePack），支持搜索、按行业筛选、详情查看、一键安装。
  @module views/execution/rule-engine/pack-market
-->
<script setup lang="ts">
/**
 * 规则集市场（P2-14）+ 知识包依赖更新提醒（P2-10）
 */
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Download, Star, Document, OfficeBuilding, Refresh, Upload } from '@element-plus/icons-vue'
import * as ruleApi from '@/api/rule-engine'
import type { RulePack, PackUpdateInfo } from '@/api/rule-engine'

const loading = ref(false)
const searchKeyword = ref('')
const selectedIndustry = ref('')
const packList = ref<RulePack[]>([])

/** 更新检查状态 */
const updateChecking = ref(false)
const updateInfoList = ref<PackUpdateInfo[]>([])
/** 有更新的知识包数量（用于徽标显示） */
const updateCount = computed(() => updateInfoList.value.filter((u) => u.hasUpdate).length)
/** 是否有可批量更新的包 */
const hasUpdatablePacks = computed(() => updateCount.value > 0)

/** 行业列表（动态从数据中提取） */
const industries = computed(() => {
  const set = new Set<string>()
  packList.value.forEach((p) => p.industry && set.add(p.industry))
  return Array.from(set)
})

async function loadList() {
  loading.value = true
  try {
    const { data } = await ruleApi.listPacks()
    packList.value = data || []
  } catch {
    packList.value = []
  } finally {
    loading.value = false
  }
}

async function doSearch() {
  loading.value = true
  try {
    const { data } = await ruleApi.searchPacks(searchKeyword.value)
    packList.value = data || []
  } catch {
    packList.value = []
  } finally {
    loading.value = false
  }
}

async function handleInstall(pack: RulePack) {
  try {
    await ElMessageBox.confirm(
      `确定要安装规则集「${pack.packName} v${pack.packVersion}」吗？\n` +
        `将导入 ${pack.ruleCodes?.length || 0} 条规则定义。`,
      '确认安装',
      { confirmButtonText: '一键安装', cancelButtonText: '取消', type: 'info' },
    )
  } catch {
    return
  }
  try {
    const { data } = await ruleApi.installPack(pack.packCode, pack.packVersion)
    if (data?.success > 0) {
      ElMessage.success(
        `安装完成：成功 ${data.success} 条，失败 ${data.failed} 条`,
      )
    } else if (data?.failed > 0) {
      ElMessage.warning(`安装部分失败：失败 ${data.failed} 条`)
    } else {
      ElMessage.info('无新增规则（已存在）')
    }
    await loadList()
    // 安装后重新检查更新
    await checkUpdates()
  } catch (e: any) {
    ElMessage.error(e?.message || '安装失败')
  }
}

// ==================== P2-10 知识包更新检查 ====================

/**
 * 检查已安装知识包的更新
 * 启动时自动调用一次，也可手动触发
 */
async function checkUpdates() {
  updateChecking.value = true
  try {
    const { data } = await ruleApi.checkPackUpdates()
    updateInfoList.value = data || []
    if (updateCount.value > 0) {
      ElMessage.info(`检测到 ${updateCount.value} 个知识包有新版本可更新`)
    }
  } catch (e: any) {
    // 静默失败，不打扰用户（启动检查）
    updateInfoList.value = []
    console.warn('[pack-market] 检查更新失败:', e?.message)
  } finally {
    updateChecking.value = false
  }
}

/**
 * 获取指定知识包的更新信息
 * @param packCode 知识包编码
 * @returns 更新信息（无则返回 null）
 */
function getUpdateInfo(packCode: string): PackUpdateInfo | null {
  return updateInfoList.value.find((u) => u.packCode === packCode && u.hasUpdate) || null
}

/**
 * 批量更新全部有更新的知识包
 */
async function handleBatchUpdate() {
  const updatables = updateInfoList.value.filter((u) => u.hasUpdate)
  if (!updatables.length) {
    ElMessage.info('暂无需要更新的知识包')
    return
  }
  const packCodes = updatables.map((u) => u.packCode)
  try {
    await ElMessageBox.confirm(
      `确认批量更新 ${packCodes.length} 个知识包？\n` +
        packCodes.map((c) => `· ${c}`).join('\n'),
      '确认批量更新',
      { confirmButtonText: '批量更新', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    const { data } = await ruleApi.batchUpdatePacks(packCodes)
    const results = data || []
    const totalSuccess = results.reduce((sum, r) => sum + (r.success || 0), 0)
    const totalFailed = results.reduce((sum, r) => sum + (r.failed || 0), 0)
    if (totalFailed > 0) {
      ElMessage.warning(`批量更新完成：成功 ${totalSuccess} 条，失败 ${totalFailed} 条`)
    } else {
      ElMessage.success(`批量更新完成：共更新 ${totalSuccess} 条规则`)
    }
    await loadList()
    await checkUpdates()
  } catch (e: any) {
    ElMessage.error(e?.message || '批量更新失败')
  }
}

/**
 * 更新单个知识包
 */
async function handleUpdateSingle(info: PackUpdateInfo) {
  try {
    await ElMessageBox.confirm(
      `确认更新知识包「${info.packName}」？\n` +
        `当前版本: ${info.installedVersion}\n` +
        `最新版本: ${info.latestVersion}`,
      '确认更新',
      { confirmButtonText: '更新', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    const { data } = await ruleApi.batchUpdatePacks([info.packCode])
    const result = data?.[0]
    if (result?.failed > 0) {
      ElMessage.warning(`更新部分失败：失败 ${result.failed} 条`)
    } else {
      ElMessage.success(`更新完成：成功 ${result?.success || 0} 条`)
    }
    await loadList()
    await checkUpdates()
  } catch (e: any) {
    ElMessage.error(e?.message || '更新失败')
  }
}

function formatRating(r: number) {
  return (r || 0).toFixed(1)
}

function getTags(pack: RulePack) {
  return pack.tags || []
}

onMounted(() => {
  loadList()
  // P2-10 启动时自动检查更新
  checkUpdates()
})
</script>

<template>
  <div class="pack-market" v-loading="loading">
    <!-- 顶部搜索栏 -->
    <div class="market-header">
      <h2 class="market-title">
        <el-icon><Document /></el-icon>
        规则集市场
      </h2>
      <div class="market-actions">
        <!-- P2-10 检查更新按钮（带徽标） -->
        <el-badge :value="updateCount" :hidden="updateCount === 0" :max="99" class="update-badge">
          <el-button :icon="Refresh" :loading="updateChecking" @click="checkUpdates">检查更新</el-button>
        </el-badge>
        <!-- P2-10 批量更新按钮（仅当有更新时显示） -->
        <el-button
          v-if="hasUpdatablePacks"
          type="warning"
          :icon="Upload"
          @click="handleBatchUpdate"
        >
          批量更新 ({{ updateCount }})
        </el-button>
        <div class="market-search">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索规则集名称、编码、标签"
            clearable
            @keyup.enter="doSearch"
            @clear="loadList"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" :icon="Search" @click="doSearch">搜索</el-button>
        </div>
      </div>
    </div>

    <!-- 行业筛选 -->
    <div class="industry-filter">
      <el-radio-group v-model="selectedIndustry" @change="doSearch">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button
          v-for="ind in industries"
          :key="ind"
          :value="ind"
        >
          <el-icon><OfficeBuilding /></el-icon>{{ ind }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <!-- 规则集卡片网格 -->
    <div v-if="packList.length === 0 && !loading" class="empty-state">
      <el-empty description="市场暂无规则集" />
    </div>
    <div v-else class="pack-grid">
      <div
        v-for="pack in packList"
        :key="pack.packCode + pack.packVersion"
        class="pack-card"
        :class="{
          'pack-card-official': pack.author === 'OFFICIAL',
          'pack-card-updatable': !!getUpdateInfo(pack.packCode),
        }"
      >
        <div class="pack-card-header">
          <div class="pack-name">{{ pack.packName }}</div>
          <el-tag v-if="pack.author === 'OFFICIAL'" type="success" size="small" effect="dark">
            官方
          </el-tag>
          <!-- P2-10 有更新徽标 -->
          <el-tag
            v-if="getUpdateInfo(pack.packCode)"
            type="warning"
            size="small"
            effect="dark"
            class="update-tag"
          >
            有更新
          </el-tag>
        </div>
        <div class="pack-meta">
          <el-tag size="small" effect="plain">v{{ pack.packVersion }}</el-tag>
          <el-tag v-if="pack.industry" size="small" type="info" effect="plain">
            {{ pack.industry }}
          </el-tag>
          <span class="pack-rules">
            {{ pack.ruleCodes?.length || 0 }} 条规则
          </span>
        </div>
        <!-- P2-10 版本对比信息 -->
        <div v-if="getUpdateInfo(pack.packCode)" class="pack-update-info">
          <span class="update-version">
            已装: v{{ getUpdateInfo(pack.packCode)?.installedVersion }}
            → 最新: v{{ getUpdateInfo(pack.packCode)?.latestVersion }}
          </span>
        </div>
        <div class="pack-desc">{{ pack.description || '—' }}</div>
        <div class="pack-tags">
          <el-tag
            v-for="tag in getTags(pack)"
            :key="tag"
            size="small"
            effect="plain"
            type="warning"
          >
            {{ tag }}
          </el-tag>
        </div>
        <div class="pack-stats">
          <span class="stat">
            <el-icon><Download /></el-icon>
            {{ pack.downloadCount || 0 }}
          </span>
          <span class="stat">
            <el-icon><Star /></el-icon>
            {{ formatRating(pack.rating) }}
          </span>
        </div>
        <div class="pack-actions">
          <el-button type="primary" :icon="Download" size="small" @click="handleInstall(pack)">
            一键安装
          </el-button>
          <!-- P2-10 单个更新按钮 -->
          <el-button
            v-if="getUpdateInfo(pack.packCode)"
            type="warning"
            :icon="Upload"
            size="small"
            @click="handleUpdateSingle(getUpdateInfo(pack.packCode)!)"
          >
            更新
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pack-market {
  padding: 16px;
  min-height: 600px;
}

.market-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .market-title {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0;
    font-size: 18px;
    color: #303133;
  }

  .market-actions {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .market-search {
    display: flex;
    gap: 8px;
    width: 360px;
  }

  .update-badge {
    margin-right: 0;
  }
}

.industry-filter {
  margin-bottom: 16px;
}

.pack-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.pack-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  transition: all 0.2s;

  &:hover {
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
    transform: translateY(-2px);
  }

  &.pack-card-official {
    border-color: #67c23a;
    background: linear-gradient(135deg, #f0f9eb 0%, #ffffff 60%);
  }

  /* P2-10 有更新的知识包高亮 */
  &.pack-card-updatable {
    border-color: #e6a23c;
    background: linear-gradient(135deg, #fdf6ec 0%, #ffffff 60%);
  }
}

.pack-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  gap: 6px;

  .pack-name {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .update-tag {
    flex-shrink: 0;
  }
}

.pack-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;

  .pack-rules {
    color: #909399;
    font-size: 12px;
  }
}

/* P2-10 版本对比信息 */
.pack-update-info {
  margin-bottom: 8px;
  padding: 6px 8px;
  background: #fdf6ec;
  border-radius: 4px;
  font-size: 12px;

  .update-version {
    color: #e6a23c;
    font-weight: 500;
  }
}

.pack-desc {
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
  min-height: 40px;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.pack-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  margin-bottom: 12px;
  min-height: 24px;
}

.pack-stats {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  font-size: 12px;
  color: #909399;

  .stat {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
}

.pack-actions {
  display: flex;
  justify-content: flex-end;
}

.empty-state {
  text-align: center;
  padding: 60px 0;
}
</style>
