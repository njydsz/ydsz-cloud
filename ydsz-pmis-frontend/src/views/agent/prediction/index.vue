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
import { aggregateByType, countByAlertLevel, getById, page, recent } from '@/api/agent/prediction'
import type { AgentPrediction, AlertLevel } from '@/api/agent/prediction/types'
import { useECharts } from '@/composables/useECharts'
import { PC } from '@/constants/permissionCodes'

const AGENT_TYPE_OPTIONS = [
  { value: '',                    label: '全部 Agent' },
  { value: 'RISK_WARNING',        label: '项目风险预警' },
  { value: 'RESOURCE_RECOMMEND',  label: '资源调度推荐' },
  { value: 'PROFIT_FORECAST',     label: '利润预测' },
  { value: 'WIN_RATE_PREDICT',    label: '商机赢率预测' },
  { value: 'TIMESHEET_ANOMALY',   label: '工时异常识别' },
]
const ALERT_OPTIONS = [
  { value: '',        label: '全部等级' },
  { value: 'RED',     label: '红色' },
  { value: 'YELLOW',  label: '黄色' },
  { value: 'NORMAL',  label: '正常' },
  { value: 'INFO',    label: '提示' },
]
const STATUS_OPTIONS = [
  { value: '',        label: '全部状态' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED',  label: '失败' },
  { value: 'RUNNING', label: '执行中' },
]

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
const aggregateData = ref<Array<{ agentType: string; count: number; red: number; yellow: number; normal: number }>>([])
const counts = reactive({ red: 0, yellow: 0, normal: 0, total: 0 })

async function load() {
  loading.value = true
  try {
    const { data } = await page(pageNo.value, pageSize.value, {
      agentType: filter.agentType || undefined,
      alertLevel: filter.alertLevel || undefined,
      status: filter.status || undefined,
    })
    list.value = (data as any)?.list ?? (data as any) ?? []
    total.value = (data as any)?.total ?? list.value.length
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
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
    aggregateData.value = ((aggRes as any)?.data || []) as any[]
    counts.red = Number((redRes as any)?.data ?? 0)
    counts.yellow = Number((yellowRes as any)?.data ?? 0)
    counts.normal = Number((normalRes as any)?.data ?? 0)
    counts.total = Number((totalRes as any)?.data ?? 0)
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
    title: { text: 'Agent 预测分布', left: 'center', top: 0 },
    tooltip: { trigger: 'axis' },
    legend: { data: ['红色', '黄色', '正常', '其他'], top: 28 },
    grid: { top: 70, left: 60, right: 40, bottom: 60 },
    xAxis: { type: 'category', data: rows.map((r) => r.agentType), axisLabel: { rotate: 20 } },
    yAxis: { type: 'value' },
    series: [
      { name: '红色',  type: 'bar', stack: 't', data: rows.map((r) => r.red ?? 0),    itemStyle: { color: '#F56C6C' } },
      { name: '黄色',  type: 'bar', stack: 't', data: rows.map((r) => r.yellow ?? 0), itemStyle: { color: '#E6A23C' } },
      { name: '正常',  type: 'bar', stack: 't', data: rows.map((r) => r.normal ?? 0), itemStyle: { color: '#67C23A' } },
      { name: '其他',  type: 'bar', stack: 't', data: rows.map((r) => Math.max(0, (r.count ?? 0) - (r.red ?? 0) - (r.yellow ?? 0) - (r.normal ?? 0))), itemStyle: { color: '#909399' } },
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
  } catch (e: any) {
    ElMessage.error(e?.message || '详情加载失败')
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
          <div class="kpi-title">总记录数</div>
          <div class="kpi-value">{{ counts.total }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="kpi-card danger">
          <div class="kpi-title">红色告警</div>
          <div class="kpi-value">{{ counts.red }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="kpi-card warning">
          <div class="kpi-title">黄色告警</div>
          <div class="kpi-value">{{ counts.yellow }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="kpi-card success">
          <div class="kpi-title">正常</div>
          <div class="kpi-value">{{ counts.normal }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="filter-card">
      <el-form inline>
        <el-form-item label="Agent 类型">
          <el-select v-model="filter.agentType" placeholder="全部" style="width: 180px" @change="onFilterChange">
            <el-option v-for="o in AGENT_TYPE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="告警等级">
          <el-select v-model="filter.alertLevel" placeholder="全部" style="width: 140px" @change="onFilterChange">
            <el-option v-for="o in ALERT_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.status" placeholder="全部" style="width: 120px" @change="onFilterChange">
            <el-option v-for="o in STATUS_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button :icon="'Refresh'" @click="load">刷新</el-button>
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
            <vxe-column field="taskCode" title="任务编码" width="200" show-overflow />
            <vxe-column field="agentType" title="Agent" width="160">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ row.agentType }}</el-tag>
              </template>
            </vxe-column>
            <vxe-column field="bizType" title="业务" width="100" />
            <vxe-column field="bizRef" title="业务编号" width="140" show-overflow />
            <vxe-column field="alertLevel" title="告警" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.alertLevel" :type="levelType(row.alertLevel)" size="small" effect="dark">
                  {{ row.alertLevel }}
                </el-tag>
              </template>
            </vxe-column>
            <vxe-column field="score" title="得分" width="80">
              <template #default="{ row }">{{ row.score == null ? '-' : Number(row.score).toFixed(2) }}</template>
            </vxe-column>
            <vxe-column field="confidence" title="置信度" width="100">
              <template #default="{ row }">{{ row.confidence == null ? '-' : Number(row.confidence).toFixed(4) }}</template>
            </vxe-column>
            <vxe-column field="status" title="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
              </template>
            </vxe-column>
            <vxe-column field="costMs" title="耗时" width="80">
              <template #default="{ row }">{{ row.costMs ?? 0 }} ms</template>
            </vxe-column>
            <vxe-column field="createdAt" title="执行时间" width="170" />
            <vxe-column title="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
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
        <el-card shadow="never" header="告警分布">
          <div ref="chartRef" class="chart-area" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="执行详情" size="520px">
      <el-skeleton v-if="detailLoading" :rows="6" animated />
      <template v-else-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="任务编码">{{ detail.taskCode }}</el-descriptions-item>
          <el-descriptions-item label="Agent 类型">
            <el-tag size="small">{{ detail.agentType }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="告警等级">
            <el-tag :type="levelType(detail.alertLevel)" size="small" effect="dark">
              {{ detail.alertLevel || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detail.status)" size="small">{{ detail.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="得分">{{ detail.score == null ? '-' : Number(detail.score).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="置信度">{{ detail.confidence == null ? '-' : Number(detail.confidence).toFixed(4) }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ detail.costMs ?? 0 }} ms</el-descriptions-item>
          <el-descriptions-item label="模型版本">{{ detail.modelVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="调用人">{{ detail.callerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ detail.source || '-' }}</el-descriptions-item>
          <el-descriptions-item label="业务编号">{{ detail.bizRef || '-' }}</el-descriptions-item>
          <el-descriptions-item label="建议">{{ detail.suggestion || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.errorMsg" label="错误信息">
            <el-text type="danger">{{ detail.errorMsg }}</el-text>
          </el-descriptions-item>
        </el-descriptions>

        <el-collapse style="margin-top: 12px">
          <el-collapse-item title="输入快照" name="input">
            <pre class="json-pre">{{ inputSnapshotFmt }}</pre>
          </el-collapse-item>
          <el-collapse-item title="输出结果" name="output">
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
