<script setup lang="ts">
/**
 * AI 智能体预测结果历史
 *
 * 展示历史执行记录：分页查询 + 类型/告警等级筛选 + 详情侧滑窗。
 * 详情面板输出 inputSnapshot / outputResult JSON 格式化 + 命中规则列表 + 建议措施。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { aggregateByType, countByAlertLevel, getById, page, recent } from '@/api/agent/prediction'
import type { AgentPrediction, AlertLevel } from '@/api/agent/prediction/types'
import { useECharts } from '@/composables/useECharts'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

const AGENT_TYPE_OPTIONS = computed(() => [
  { value: '',                    label: t('agent.prediction.agentType.ALL') },
  { value: 'RISK_WARNING',        label: t('agent.prediction.agentType.RISK_WARNING') },
  { value: 'RESOURCE_RECOMMEND',  label: t('agent.prediction.agentType.RESOURCE_RECOMMEND') },
  { value: 'PROFIT_FORECAST',     label: t('agent.prediction.agentType.PROFIT_FORECAST') },
  { value: 'WIN_RATE_PREDICT',    label: t('agent.prediction.agentType.WIN_RATE_PREDICT') },
  { value: 'TIMESHEET_ANOMALY',   label: t('agent.prediction.agentType.TIMESHEET_ANOMALY') },
])
const ALERT_OPTIONS = computed(() => [
  { value: '',        label: t('agent.prediction.alert.ALL') },
  { value: 'RED',     label: t('agent.prediction.alert.RED') },
  { value: 'YELLOW',  label: t('agent.prediction.alert.YELLOW') },
  { value: 'NORMAL',  label: t('agent.prediction.alert.NORMAL') },
  { value: 'INFO',    label: t('agent.prediction.alert.INFO') },
])
const STATUS_OPTIONS = computed(() => [
  { value: '',        label: t('agent.prediction.status.ALL') },
  { value: 'SUCCESS', label: t('agent.prediction.status.SUCCESS') },
  { value: 'FAILED',  label: t('agent.prediction.status.FAILED') },
  { value: 'RUNNING', label: t('agent.prediction.status.RUNNING') },
])

const filter = reactive({
  agentType: '',
  alertLevel: '' as AlertLevel | '',
  status: '',
})
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)
const list = ref<AgentPrediction[]>([])
const loading = ref(false)
/** 按类型聚合的统计行 */
type AggregateRow = { agentType: string; count: number; red: number; yellow: number; normal: number }
const aggregateData = ref<AggregateRow[]>([])
const counts = reactive({ red: 0, yellow: 0, normal: 0, total: 0 })

async function load() {
  loading.value = true
  try {
    const { data } = await page(pageNo.value, pageSize.value, {
      agentType: filter.agentType || undefined,
      alertLevel: filter.alertLevel || undefined,
      status: filter.status || undefined,
    })
    list.value = data?.list ?? []
    total.value = data?.total ?? list.value.length
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || t('agent.prediction.messages.loadFailed'))
  } finally {
    loading.value = false
  }
}

/** 并发加载按类型聚合与各告警等级计数，完成后渲染图表 */
async function loadAggregate() {
  try {
    const [aggRes, redRes, yellowRes, normalRes, totalRes] = await Promise.all([
      aggregateByType(),
      countByAlertLevel({ alertLevel: 'RED' }),
      countByAlertLevel({ alertLevel: 'YELLOW' }),
      countByAlertLevel({ alertLevel: 'NORMAL' }),
      countByAlertLevel(),
    ])
    aggregateData.value = (aggRes?.data as AggregateRow[]) || []
    counts.red = Number(redRes?.data ?? 0)
    counts.yellow = Number(yellowRes?.data ?? 0)
    counts.normal = Number(normalRes?.data ?? 0)
    counts.total = Number(totalRes?.data ?? 0)
    await nextTickRender()
  } catch { /* 静默 */ }
}

const chartRef = ref<HTMLDivElement | null>(null)
const { setOption } = useECharts(chartRef)

function nextTickRender() {
  return new Promise<void>((r) => requestAnimationFrame(() => r()))
}

function renderChart() {
  const rows = aggregateData.value
  setOption({
    title: { text: t('agent.prediction.chart.title'), left: 'center', top: 0 },
    tooltip: { trigger: 'axis' },
    legend: { data: [t('agent.prediction.chart.legendRed'), t('agent.prediction.chart.legendYellow'), t('agent.prediction.chart.legendNormal'), t('agent.prediction.chart.legendOther')], top: 28 },
    grid: { top: 70, left: 60, right: 40, bottom: 60 },
    xAxis: { type: 'category', data: rows.map((r) => r.agentType), axisLabel: { rotate: 20 } },
    yAxis: { type: 'value' },
    series: [
      { name: t('agent.prediction.chart.legendRed'),  type: 'bar', stack: 't', data: rows.map((r) => r.red ?? 0),    itemStyle: { color: '#F56C6C' } },
      { name: t('agent.prediction.chart.legendYellow'),  type: 'bar', stack: 't', data: rows.map((r) => r.yellow ?? 0), itemStyle: { color: '#E6A23C' } },
      { name: t('agent.prediction.chart.legendNormal'),  type: 'bar', stack: 't', data: rows.map((r) => r.normal ?? 0), itemStyle: { color: '#67C23A' } },
      { name: t('agent.prediction.chart.legendOther'),  type: 'bar', stack: 't', data: rows.map((r) => Math.max(0, (r.count ?? 0) - (r.red ?? 0) - (r.yellow ?? 0) - (r.normal ?? 0))), itemStyle: { color: '#909399' } },
    ],
  })
}

watch(aggregateData, () => renderChart(), { deep: true })

// 详情抽屉
const drawerVisible = ref(false)
const detail = ref<AgentPrediction | null>(null)
const detailLoading = ref(false)

async function openDetail(row: AgentPrediction) {
  drawerVisible.value = true
  detailLoading.value = true
  try {
    const { data } = await getById(row.id)
    detail.value = (data as AgentPrediction) ?? null
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || t('agent.prediction.messages.detailLoadFailed'))
  } finally {
    detailLoading.value = false
  }
}

function levelType(level?: string): 'success' | 'info' | 'warning' | 'danger' {
  switch (level) {
    case 'RED': return 'danger'
    case 'YELLOW': return 'warning'
    case 'NORMAL': return 'success'
    default: return 'info'
  }
}

function statusType(s?: string): 'success' | 'info' | 'warning' | 'danger' {
  switch (s) {
    case 'SUCCESS': return 'success'
    case 'RUNNING': return 'warning'
    case 'FAILED':  return 'danger'
    default: return 'info'
  }
}

const inputSnapshotFmt = computed(() => {
  if (!detail.value?.inputSnapshot) return ''
  try { return JSON.stringify(JSON.parse(detail.value.inputSnapshot), null, 2) } catch { return detail.value.inputSnapshot }
})
const outputResultFmt = computed(() => {
  if (!detail.value?.outputResult) return ''
  try { return JSON.stringify(JSON.parse(detail.value.outputResult), null, 2) } catch { return detail.value.outputResult }
})

async function loadRecent() {
  try {
    const { data } = await recent({ limit: 10 })
    if (Array.isArray(data) && data.length && list.value.length === 0) {
      list.value = data
      total.value = data.length
    }
  } catch { /* 静默 */ }
}

/** 翻页回调 */
function onPageChange(p: number) { pageNo.value = p; load() }
/** 每页条数变更回调 */
function onSizeChange(s: number) { pageSize.value = s; pageNo.value = 1; load() }
/** 筛选条件变更回调，重置到第一页后加载 */
function onFilterChange() { pageNo.value = 1; load() }

onMounted(() => {
  load()
  loadAggregate()
  loadRecent()
})
</script>

<template>
  <div class="agent-prediction-page">
    <!-- 统计 KPI -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-title">{{ t('agent.prediction.kpi.total') }}</div>
          <div class="kpi-value">{{ counts.total }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="kpi-card danger">
          <div class="kpi-title">{{ t('agent.prediction.kpi.red') }}</div>
          <div class="kpi-value">{{ counts.red }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="kpi-card warning">
          <div class="kpi-title">{{ t('agent.prediction.kpi.yellow') }}</div>
          <div class="kpi-value">{{ counts.yellow }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="kpi-card success">
          <div class="kpi-title">{{ t('agent.prediction.kpi.normal') }}</div>
          <div class="kpi-value">{{ counts.normal }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="filter-card">
      <el-form inline>
        <el-form-item :label="t('agent.prediction.search.agentType')">
          <el-select v-model="filter.agentType" :placeholder="t('common.all')" style="width: 180px" @change="onFilterChange">
            <el-option v-for="o in AGENT_TYPE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('agent.prediction.search.alertLevel')">
          <el-select v-model="filter.alertLevel" :placeholder="t('common.all')" style="width: 140px" @change="onFilterChange">
            <el-option v-for="o in ALERT_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('agent.prediction.search.status')">
          <el-select v-model="filter.status" :placeholder="t('common.all')" style="width: 120px" @change="onFilterChange">
            <el-option v-for="o in STATUS_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button :icon="'Refresh'" @click="load">{{ t('agent.prediction.buttons.refresh') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :xs="24" :md="16">
        <el-card shadow="never">
          <vxe-table
            v-permission="[PC.AGENT_PREDICTION_VIEW]"
            :data="list"
            :loading="loading"
            stripe
            height="auto"
          >
            <vxe-column type="seq" width="56" title="#" />
            <vxe-column field="taskCode" :title="t('agent.prediction.columns.taskCode')" width="200" show-overflow />
            <vxe-column field="agentType" :title="t('agent.prediction.columns.agent')" width="160">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ row.agentType }}</el-tag>
              </template>
            </vxe-column>
            <vxe-column field="bizType" :title="t('agent.prediction.columns.bizType')" width="100" />
            <vxe-column field="bizRef" :title="t('agent.prediction.columns.bizRef')" width="140" show-overflow />
            <vxe-column field="alertLevel" :title="t('agent.prediction.columns.alert')" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.alertLevel" :type="levelType(row.alertLevel)" size="small" effect="dark">
                  {{ row.alertLevel }}
                </el-tag>
              </template>
            </vxe-column>
            <vxe-column field="score" :title="t('agent.prediction.columns.score')" width="80">
              <template #default="{ row }">{{ row.score == null ? '-' : Number(row.score).toFixed(2) }}</template>
            </vxe-column>
            <vxe-column field="confidence" :title="t('agent.prediction.columns.confidence')" width="100">
              <template #default="{ row }">{{ row.confidence == null ? '-' : Number(row.confidence).toFixed(4) }}</template>
            </vxe-column>
            <vxe-column field="status" :title="t('agent.prediction.columns.status')" width="90">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
              </template>
            </vxe-column>
            <vxe-column field="costMs" :title="t('agent.prediction.columns.cost')" width="80">
              <template #default="{ row }">{{ row.costMs ?? 0 }} ms</template>
            </vxe-column>
            <vxe-column field="createdAt" :title="t('agent.prediction.columns.createdAt')" width="170" />
            <vxe-column :title="t('agent.prediction.columns.action')" width="80" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openDetail(row)">{{ t('agent.prediction.buttons.detail') }}</el-button>
              </template>
            </vxe-column>
          </vxe-table>
          <el-pagination
            v-model:current-page="pageNo"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            style="margin-top: 12px; justify-content: flex-end"
            @current-change="onPageChange"
            @size-change="onSizeChange"
          />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never" :header="t('agent.prediction.chartCardTitle')">
          <div ref="chartRef" class="chart-area" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" :title="t('agent.prediction.detail.title')" size="520px">
      <el-skeleton v-if="detailLoading" :rows="6" animated />
      <template v-else-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="t('agent.prediction.detail.taskCode')">{{ detail.taskCode }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.prediction.detail.agentType')">
            <el-tag size="small">{{ detail.agentType }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('agent.prediction.detail.alertLevel')">
            <el-tag :type="levelType(detail.alertLevel)" size="small" effect="dark">
              {{ detail.alertLevel || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('agent.prediction.detail.status')">
            <el-tag :type="statusType(detail.status)" size="small">{{ detail.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('agent.prediction.detail.score')">{{ detail.score == null ? '-' : Number(detail.score).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.prediction.detail.confidence')">{{ detail.confidence == null ? '-' : Number(detail.confidence).toFixed(4) }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.prediction.detail.cost')">{{ detail.costMs ?? 0 }} ms</el-descriptions-item>
          <el-descriptions-item :label="t('agent.prediction.detail.modelVersion')">{{ detail.modelVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.prediction.detail.caller')">{{ detail.callerName || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.prediction.detail.source')">{{ detail.source || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.prediction.detail.bizRef')">{{ detail.bizRef || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.prediction.detail.suggestion')">{{ detail.suggestion || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.errorMsg" :label="t('agent.prediction.detail.errorMsg')">
            <el-text type="danger">{{ detail.errorMsg }}</el-text>
          </el-descriptions-item>
        </el-descriptions>

        <el-collapse style="margin-top: 12px">
          <el-collapse-item :title="t('agent.prediction.detailCollapse.input')" name="input">
            <pre class="json-pre">{{ inputSnapshotFmt }}</pre>
          </el-collapse-item>
          <el-collapse-item :title="t('agent.prediction.detailCollapse.output')" name="output">
            <pre class="json-pre">{{ outputResultFmt }}</pre>
          </el-collapse-item>
        </el-collapse>
      </template>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
.agent-prediction-page {
  .kpi-row { margin-bottom: 16px; }
  .kpi-card {
    text-align: center;
    .kpi-title { font-size: 12px; color: var(--el-text-color-secondary); }
    .kpi-value { font-size: 22px; font-weight: 600; margin-top: 8px; }
    &.danger .kpi-value { color: var(--el-color-danger); }
    &.warning .kpi-value { color: var(--el-color-warning); }
    &.success .kpi-value { color: var(--el-color-success); }
  }
  .filter-card { margin-bottom: 0; }
  .chart-area { width: 100%; height: 320px; }
  .json-pre {
    background: var(--el-fill-color-light);
    padding: 8px;
    border-radius: 4px;
    font-size: 12px;
    max-height: 240px;
    overflow: auto;
    margin: 0;
  }
}
</style>
