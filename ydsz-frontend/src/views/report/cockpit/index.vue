<!--
  @file 经营驾驶舱
  @description 项目经营数据驾驶舱，展示 KPI 概览、趋势图、合同年度走势、部门下钻分析等。
  @module views/report/cockpit
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import PageLayout from '@/components/common/PageLayout.vue'

const { t } = useI18n()

/** 驾驶舱加载状态 */
const loading = ref(false)
/** 当前激活的 Tab 名称 */
const activeTab = ref('overview')
/** KPI 概览数据 */
const kpiData = ref({
  totalRevenue: 0,
  totalCost: 0,
  totalProfit: 0,
  profitMargin: 0,
  activeProjects: 0,
  activeContracts: 0,
  avgUtilization: 0,
  riskCount: 0,
})

/** 趋势图数据 */
const trendData = ref<any[]>([])

/** 加载驾驶舱数据 */
async function loadData() {
  loading.value = true
  try {
    // KPI and trend data will be loaded from API
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
      <h2 class="text-lg font-semibold">{{ t('common.cockpit') }}</h2>
    </template>

    <el-tabs v-model="activeTab" v-loading="loading">
      <el-tab-pane label="KPI 概览" name="overview">
        <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
          <el-card shadow="hover" v-for="(item, idx) in [
            { label: '总收入', value: kpiData.totalRevenue, prefix: '¥', color: '#409EFF' },
            { label: '总成本', value: kpiData.totalCost, prefix: '¥', color: '#E6A23C' },
            { label: '总利润', value: kpiData.totalProfit, prefix: '¥', color: '#67C23A' },
            { label: '利润率', value: kpiData.profitMargin, suffix: '%', color: '#67C23A' },
            { label: '活跃项目', value: kpiData.activeProjects, color: '#409EFF' },
            { label: '活跃合同', value: kpiData.activeContracts, color: '#909399' },
            { label: '平均利用率', value: kpiData.avgUtilization, suffix: '%', color: '#F56C6C' },
            { label: '风险数', value: kpiData.riskCount, color: '#F56C6C' },
          ]" :key="idx">
            <div class="text-center">
              <div class="text-sm text-gray-500 mb-2">{{ item.label }}</div>
              <div class="text-2xl font-bold" :style="{ color: item.color }">
                {{ item.prefix || '' }}{{ (item.value || 0).toLocaleString() }}{{ item.suffix || '' }}
              </div>
            </div>
          </el-card>
        </div>
      </el-tab-pane>

      <el-tab-pane label="趋势分析" name="trend">
        <el-card shadow="never">
          <el-empty description="趋势图表（收入/成本/利润 月度趋势）" />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="合同年度走势" name="contract-trend">
        <el-card shadow="never">
          <el-empty description="合同年度签订走势图" />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="部门下钻" name="drill-dept">
        <el-card shadow="never">
          <el-empty description="按部门维度的数据下钻分析" />
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </PageLayout>
</template>
