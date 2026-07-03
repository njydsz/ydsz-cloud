<!--
  @file 职级管理
  @description 职级管理页面：左侧展示职级体系（L1-L18）并按段位（初级/中级/高级/专家/战略）分类，右侧展示所选职级的生效费率（对外报价、对内成本、毛利率、社保公积金、月综合成本等）及历史版本。对应路由 /resource/job-level，后端服务 ydsz-pmis-iam（端口 9002）。
  @module views/resource/job-level
-->
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listJobLevels, getJobLevelRate, listJobLevelRateVersions } from '@/api/resource/job-level'
import type { JobLevelVO, JobLevelRateVO } from '@/api/resource/job-level/types'

const levels = ref<JobLevelVO[]>([])
const rate = ref<JobLevelRateVO | null>(null)
const versions = ref<JobLevelRateVO[]>([])
const selectedLevel = ref<string>('')
const loading = ref(false)

const segmentMap: Record<string, string> = {
  PRIMARY: '初级',
  MIDDLE: '中级',
  SENIOR: '高级',
  EXPERT: '专家',
  STRATEGIC: '战略',
}

/** 拉取职级列表，并默认选中首个职级展示其费率 */
async function fetchLevels() {
  loading.value = true
  try {
    const { data } = await listJobLevels()
    levels.value = data || []
    if (levels.value.length > 0) {
      selectLevel(levels.value[0].levelCode)
    }
  } finally {
    loading.value = false
  }
}

/**
 * 选中指定职级，拉取其当日生效费率与历史版本
 * @param code 职级编码（如 L8）
 */
async function selectLevel(code: string) {
  selectedLevel.value = code
  try {
    const today = new Date().toISOString().slice(0, 10)
    const { data } = await getJobLevelRate(code, today)
    rate.value = data
  } catch {
    rate.value = null
  }
  try {
    const { data } = await listJobLevelRateVersions(code)
    versions.value = data || []
  } catch {
    versions.value = []
  }
}

/** 金额格式化（元 → ¥1,234.56） */
function formatMoney(v?: number) {
  if (v === undefined || v === null) return '-'
  return `¥${Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`
}

/** 利用率小数格式化为百分比（0.85 → 85%） */
function formatPct(v?: number) {
  if (v === undefined || v === null) return '-'
  return `${(Number(v) * 100).toFixed(0)}%`
}

onMounted(fetchLevels)
</script>

<template>
  <div class="job-level-page">
    <el-row :gutter="16">
      <el-col :span="6">
        <!-- 职级列表 -->
        <el-card shadow="never" class="level-card">
          <template #header>
            <span>职级体系 (L1 - L18)</span>
          </template>
          <div
            v-for="lv in levels"
            :key="lv.levelCode"
            class="level-item"
            :class="{ active: selectedLevel === lv.levelCode }"
            @click="selectLevel(lv.levelCode)"
          >
            <div class="level-item-main">
              <el-tag size="small" :type="selectedLevel === lv.levelCode ? 'primary' : 'info'">
                {{ lv.levelCode }}
              </el-tag>
              <span class="level-name">{{ lv.levelName }}</span>
            </div>
            <el-tag size="small" type="info" effect="plain">
              {{ segmentMap[lv.levelSegment || ''] || lv.levelSegment || '-' }}
            </el-tag>
          </div>
          <el-empty v-if="!loading && levels.length === 0" description="暂无职级数据" :image-size="60" />
        </el-card>
      </el-col>

      <el-col :span="18">
        <!-- 生效费率详情 -->
        <el-card shadow="never" class="rate-card">
          <template #header>
            <div class="card-header">
              <span>{{ selectedLevel ? `${selectedLevel} - 生效费率` : '生效费率' }}</span>
              <el-tag v-if="rate" type="success">V{{ rate.version }}</el-tag>
            </div>
          </template>

          <el-empty v-if="!rate" description="该职级暂未配置生效费率" :image-size="80" />
          <el-descriptions v-else :column="3" border>
            <el-descriptions-item label="对外报价 (元/天)">
              <span class="highlight">{{ formatMoney(rate.externalDaily) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="对内成本 (元/天)">
              <span class="highlight">{{ formatMoney(rate.internalDaily) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="毛利率(预估)">
              {{
                rate.externalDaily && rate.internalDaily
                  ? `${(((Number(rate.externalDaily) - Number(rate.internalDaily)) / Number(rate.externalDaily)) * 100).toFixed(1)}%`
                  : '-'
              }}
            </el-descriptions-item>
            <el-descriptions-item label="基本工资">{{ formatMoney(rate.baseSalary) }}</el-descriptions-item>
            <el-descriptions-item label="公司社保">{{ formatMoney(rate.socialCompany) }}</el-descriptions-item>
            <el-descriptions-item label="个人社保">{{ formatMoney(rate.socialPersonal) }}</el-descriptions-item>
            <el-descriptions-item label="公司公积金">{{ formatMoney(rate.fundCompany) }}</el-descriptions-item>
            <el-descriptions-item label="个人公积金">{{ formatMoney(rate.fundPersonal) }}</el-descriptions-item>
            <el-descriptions-item label="税后到手">{{ formatMoney(rate.takeHome) }}</el-descriptions-item>
            <el-descriptions-item label="月综合成本">{{ formatMoney(rate.totalCost) }}</el-descriptions-item>
            <el-descriptions-item label="可计费利用率目标">{{ formatPct(rate.billableTarget) }}</el-descriptions-item>
            <el-descriptions-item label="生效日期">{{ rate.effectiveDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="失效日期" :span="2">{{ rate.expireDate || '长期' }}</el-descriptions-item>
            <el-descriptions-item v-if="rate.description" label="备注" :span="3">{{ rate.description }}</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 历史版本 -->
        <el-card shadow="never" class="version-card" style="margin-top: 16px">
          <template #header>
            <span>历史版本</span>
          </template>
          <vxe-table :data="versions" border>
            <vxe-column type="seq" title="#" width="50" />
            <vxe-column field="version" title="版本" width="80" align="center" />
            <vxe-column field="externalDaily" title="对外 (元/天)" width="140" />
            <vxe-column field="internalDaily" title="对内 (元/天)" width="140" />
            <vxe-column field="baseSalary" title="基本工资" width="120" />
            <vxe-column field="totalCost" title="月综合成本" width="120" />
            <vxe-column field="effectiveDate" title="生效日期" width="120" />
            <vxe-column field="expireDate" title="失效日期" width="120" />
            <vxe-column field="description" title="备注" min-width="200" />
          </vxe-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style lang="scss" scoped>
.job-level-page {
  .level-card { min-height: 600px; }

  .level-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 12px;
    border-radius: 4px;
    cursor: pointer;
    margin-bottom: 4px;
    transition: background 0.2s;

    &:hover { background: var(--el-fill-color-light); }
    &.active { background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
  }

  .level-item-main { display: flex; align-items: center; gap: 8px; }
  .level-name { font-weight: 500; }

  .rate-card { min-height: 320px; }
  .card-header { display: flex; align-items: center; justify-content: space-between; }
  .highlight { font-size: 18px; font-weight: 600; color: var(--el-color-primary); }
}
</style>
