<!--
  @file 人员利用率
  @description 可计费利用率统计与考核页面：支持团队整体均值(实时聚合+快照兜底)、等级分布、人效排行榜 TOP20、预警员工列表、快照重算(增量/强制)，对应路由 /execution/utilization
  @module views/execution/utilization
-->
<script setup lang="ts">
/**
 * 可计费利用率统计与考核
 *
 *  功能：
 *  1) 团队整体均值（实时聚合 + 快照兜底）
 *  2) 等级分布（EXCELLENT/GOOD/NORMAL/WARN/CRITICAL）
 *  3) 排行榜 TOP 20
 *  4) 预警员工列表
 *  5) 触发快照重算（运维 / 手动补算）
 *
 *  数据源：pmis_execution_time_entry（status=APPROVED）
 *  快照：  pmis_billable_utilization_snapshot（cronjob 每日 02:30 计算）
 */
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import VirtualTable from '@/components/common/VirtualTable.vue'
import type { ColumnConfig } from '@/components/common/VirtualTable.vue'
import {
  aggregateUtilization,
  getOverallUtilization,
  getUtilizationRank,
  getUtilizationAlerts,
  recomputeUtilization,
  getSnapshotAverage,
} from '@/api/execution/utilization'
import type { UtilizationRowVO, UtilizationOverallVO } from '@/api/execution/utilization'
import { PC } from '@/constants/permissionCodes'
import { useUserStore } from '@/store/modules/user'

// 权限助手：统一通过 userStore 校验按钮级权限
const userStore = useUserStore()
const hasPerm = (code: string) => userStore.hasPermission(code)

// 列表加载状态
const loading = ref(false)
const snapshotLoading = ref(false)
const recomputeLoading = ref(false)

// 查询条件：日期区间 + 快照周期(yyyy-MM)
const query = reactive({
  from: '',
  to: '',
  period: new Date().toISOString().slice(0, 7),
})

// 各区块数据：聚合明细 / 排行榜 / 预警 / 整体统计 / 快照均值
const aggregate = ref<UtilizationRowVO[]>([])
const rank = ref<UtilizationRowVO[]>([])
const alerts = ref<UtilizationRowVO[]>([])
const overall = ref<UtilizationOverallVO | null>(null)
const snapshotAvg = ref<Record<string, unknown> | null>(null)

/** 默认查询区间：本月 1 号 ~ 今天 */
function defaultRange() {
  const now = new Date()
  const y = now.getFullYear()
  const m = (now.getMonth() + 1).toString().padStart(2, '0')
  query.from = `${y}-${m}-01`
  query.to = now.toISOString().slice(0, 10)
}

// 考核等级颜色映射：优秀/良好/合格/黄色预警/红色预警
const gradeColorMap: Record<string, string> = {
  EXCELLENT: '#67c23a',
  GOOD: '#409eff',
  NORMAL: '#909399',
  WARN: '#e6a23c',
  CRITICAL: '#f56c6c',
}

// 考核等级文案映射
const gradeLabelMap: Record<string, string> = {
  EXCELLENT: '优秀',
  GOOD: '良好',
  NORMAL: '合格',
  WARN: '黄色预警',
  CRITICAL: '红色预警',
}

/** 预警员工列表列配置（P3-1: 迁移到 VirtualTable，去掉了 el-table 的序号列） */
const alertColumns: ColumnConfig[] = [
  { field: 'employeeName', title: '员工', width: 140 },
  { field: 'levelCode', title: '职级', width: 80 },
  { field: 'totalHours', title: '总工时 (h)', width: 120, align: 'right', slot: true },
  { field: 'billableHours', title: '可计费 (h)', width: 120, align: 'right', slot: true },
  { field: 'utilizationPct', title: '利用率', width: 120, align: 'right', slot: true },
  { field: 'grade', title: '考核', width: 120, align: 'center', slot: true },
]

/** 整体考核等级（取后端返回值，缺省为 NORMAL） */
const overallGrade = computed(() => {
  return overall.value?.grade ?? 'NORMAL'
})

/** 考核等级分布：统计 aggregate 中各等级人数 */
const gradeDistribution = computed(() => {
  const map: Record<string, number> = {
    EXCELLENT: 0,
    GOOD: 0,
    NORMAL: 0,
    WARN: 0,
    CRITICAL: 0,
  }
  aggregate.value.forEach((r) => {
    if (r.grade && map[r.grade] !== undefined) map[r.grade]++
  })
  return map
})

/** 拉取团队整体利用率统计（含总工时/可计费工时/参与人数/等级） */
async function fetchOverall() {
  loading.value = true
  try {
    const { data } = await getOverallUtilization(query.from, query.to)
    overall.value = data ?? null
  } finally {
    loading.value = false
  }
}

/** 拉取人效排行榜 TOP 20（按利用率倒序） */
async function fetchRank() {
  loading.value = true
  try {
    const { data } = await getUtilizationRank(query.from, query.to, 20)
    rank.value = (data as UtilizationRowVO[]) ?? []
  } finally {
    loading.value = false
  }
}

/** 拉取预警员工列表（WARN/CRITICAL，按利用率升序） */
async function fetchAlerts() {
  loading.value = true
  try {
    const { data } = await getUtilizationAlerts(query.from, query.to)
    alerts.value = (data as UtilizationRowVO[]) ?? []
  } finally {
    loading.value = false
  }
}

/** 拉取快照周期均值（cronjob 每日 02:30 计算的快照数据） */
async function fetchSnapshot() {
  snapshotLoading.value = true
  try {
    const { data } = await getSnapshotAverage(query.period)
    snapshotAvg.value = (data as Record<string, unknown>) ?? null
  } finally {
    snapshotLoading.value = false
  }
}

/** 拉取利用率聚合明细（用于等级分布统计） */
async function fetchAggregate() {
  loading.value = true
  try {
    const { data } = await aggregateUtilization(query.from, query.to)
    aggregate.value = (data as UtilizationRowVO[]) ?? []
  } finally {
    loading.value = false
  }
}

/** 刷新全部数据：并发拉取聚合/整体/排行/预警/快照 */
async function refresh() {
  await Promise.all([fetchAggregate(), fetchOverall(), fetchRank(), fetchAlerts(), fetchSnapshot()])
}

/** 重置查询条件为默认区间并刷新全部数据 */
function handleReset() {
  defaultRange()
  query.period = new Date().toISOString().slice(0, 7)
  refresh()
}

/** 触发快照重算：recomputeAll=true 强制重写，false 增量补算 */
async function handleRecompute(recomputeAll: boolean) {
  try {
    await ElMessageBox.confirm(
      recomputeAll
        ? `确认要强制重算周期 ${query.period} 的可计费利用率快照？该操作将清空已有快照后重写。`
        : `确认要增量重算周期 ${query.period} 的可计费利用率快照？`,
      '提示',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  recomputeLoading.value = true
  try {
    const { data } = await recomputeUtilization(query.period, recomputeAll)
    ElMessage.success(
      `重算完成：affectedCount=${(data as { affectedCount?: number })?.affectedCount ?? 0}`,
    )
    await fetchSnapshot()
  } finally {
    recomputeLoading.value = false
  }
}

onMounted(() => {
  defaultRange()
  refresh()
})

/** 百分比格式化：入参为小数(0.85 → 85.00%)，空值返回 0.00% */
function fmtPct(v: number | undefined) {
  if (v === undefined || v === null) return '0.00%'
  return `${(v * 100).toFixed(2)}%`
}
/** 工时格式化：保留两位小数，空值返回 0.00 */
function fmtHours(v: number | undefined) {
  if (v === undefined || v === null) return '0.00'
  return Number(v).toFixed(2)
}
</script>

<template>
  <PageLayout title="可计费利用率统计">
    <!-- 头部查询区 -->
    <el-card class="filter-bar" shadow="never">
      <el-form :inline="true" :model="query">
        <el-form-item label="开始日期">
          <el-date-picker
            v-model="query.from"
            type="date"
            value-format="YYYY-MM-DD"
            :placeholder="$t('execution.utilization.filter.startDate')"
            aria-label="开始日期"
          />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker
            v-model="query.to"
            type="date"
            value-format="YYYY-MM-DD"
            :placeholder="$t('execution.utilization.filter.endDate')"
            aria-label="结束日期"
          />
        </el-form-item>
        <el-form-item label="快照周期">
          <el-input v-model="query.period" :placeholder="$t('execution.utilization.filter.periodPlaceholder')" style="width: 140px" aria-label="快照周期" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" aria-label="查询利用率" @click="refresh">查询</el-button>
          <el-button aria-label="重置查询条件" @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item v-if="hasPerm(PC.EXECUTION_UTILIZATION_RECOMPUTE)">
          <el-button
            type="warning"
            :loading="recomputeLoading"
            plain
            aria-label="增量重算快照"
            @click="handleRecompute(false)"
          >
            增量重算快照
          </el-button>
          <el-button
            type="danger"
            :loading="recomputeLoading"
            plain
            aria-label="强制重算快照"
            @click="handleRecompute(true)"
          >
            强制重算快照
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-label">整体利用率</div>
          <div class="kpi-value" :style="{ color: gradeColorMap[overallGrade] }">
            {{ fmtPct(overall?.utilizationPct) }}
          </div>
          <div class="kpi-foot">考核等级：{{ gradeLabelMap[overallGrade] }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-label">总工时 / 可计费工时</div>
          <div class="kpi-value">{{ fmtHours(overall?.totalHours) }}h</div>
          <div class="kpi-foot">可计费：{{ fmtHours(overall?.billableHours) }}h</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-label">参与员工数</div>
          <div class="kpi-value">{{ overall?.employeeCount ?? 0 }}</div>
          <div class="kpi-foot">区间内提交工时人数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-label">快照周期 {{ query.period }}</div>
          <div class="kpi-value">
            {{ fmtPct(snapshotAvg?.avg_pct as number | undefined) }}
          </div>
          <div class="kpi-foot">
            来源：{{ snapshotAvg?.source ?? '—' }}（预警：
            {{ snapshotAvg?.warn_count ?? 0 }} 黄 / {{ snapshotAvg?.critical_count ?? 0 }} 红）
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 等级分布 -->
    <el-card shadow="never" class="block">
      <template #header>考核等级分布</template>
      <el-row :gutter="12">
        <el-col
          v-for="(count, g) in gradeDistribution"
          :key="g"
          :span="Math.max(4, Math.floor(24 / 5))"
        >
          <div class="grade-card" :style="{ borderColor: gradeColorMap[g as string] }">
            <div class="grade-label" :style="{ color: gradeColorMap[g as string] }">
              {{ gradeLabelMap[g as string] ?? g }}
            </div>
            <div class="grade-count">{{ count }}</div>
            <div class="grade-desc">人</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 排行榜 -->
    <el-card shadow="never" class="block">
      <template #header>
        <span>人效排行榜 TOP 20</span>
        <span class="muted">（按 utilizationPct 倒序）</span>
      </template>
      <el-table v-loading="loading" :data="rank" border size="small">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="employeeName" label="员工" min-width="120" />
        <el-table-column prop="levelCode" label="职级" width="80" />
        <el-table-column label="总工时 (h)" width="120" align="right">
          <template #default="{ row }">{{ fmtHours(row.totalHours) }}</template>
        </el-table-column>
        <el-table-column label="可计费 (h)" width="120" align="right">
          <template #default="{ row }">{{ fmtHours(row.billableHours) }}</template>
        </el-table-column>
        <el-table-column label="加班 (h)" width="100" align="right">
          <template #default="{ row }">{{ fmtHours(row.overtimeHours) }}</template>
        </el-table-column>
        <el-table-column label="利用率" width="120" align="right">
          <template #default="{ row }">
            <span :style="{ color: gradeColorMap[row.grade] || '' }">
              {{ fmtPct(row.utilizationPct) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="考核" width="120" align="center">
          <template #default="{ row }">
            <StatusTag :value="row.grade" :label="gradeLabelMap[row.grade] || row.grade" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 预警员工 -->
    <el-card shadow="never" class="block">
      <template #header>
        <span style="color: #f56c6c">预警员工（WARN/CRITICAL）</span>
        <span class="muted">（按 utilizationPct 升序）</span>
      </template>
      <!-- P3-1: 已迁移到 VirtualTable，支持虚拟滚动 + 自定义插槽；去掉了原 el-table 的序号列 -->
      <VirtualTable
        :data="alerts as Record<string, unknown>[]"
        :columns="alertColumns"
        :loading="loading"
        :height="500"
      >
        <template #col-totalHours="{ row }">
          {{ fmtHours((row as UtilizationRowVO).totalHours) }}
        </template>
        <template #col-billableHours="{ row }">
          {{ fmtHours((row as UtilizationRowVO).billableHours) }}
        </template>
        <template #col-utilizationPct="{ row }">
          <span :style="{ color: gradeColorMap[(row as UtilizationRowVO).grade || ''] || '' }">
            {{ fmtPct((row as UtilizationRowVO).utilizationPct) }}
          </span>
        </template>
        <template #col-grade="{ row }">
          <StatusTag
            :value="(row as UtilizationRowVO).grade"
            :label="gradeLabelMap[(row as UtilizationRowVO).grade || ''] || (row as UtilizationRowVO).grade"
          />
        </template>
      </VirtualTable>
      <el-empty v-if="!loading && alerts.length === 0" description="当前无预警员工" />
    </el-card>
  </PageLayout>
</template>

<style scoped lang="scss">
.filter-bar {
  margin-bottom: 16px;
}
.kpi-row {
  margin-bottom: 16px;
}
.kpi-card {
  text-align: center;
  .kpi-label {
    color: #909399;
    font-size: 13px;
    margin-bottom: 8px;
  }
  .kpi-value {
    font-size: 28px;
    font-weight: 600;
    margin-bottom: 4px;
  }
  .kpi-foot {
    color: #606266;
    font-size: 12px;
  }
}
.block {
  margin-bottom: 16px;
}
.muted {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
.grade-card {
  border: 1px solid #ebeef5;
  border-left-width: 4px;
  border-radius: 4px;
  padding: 12px;
  text-align: center;
  background: #fafafa;
  .grade-label {
    font-weight: 600;
    font-size: 14px;
    margin-bottom: 4px;
  }
  .grade-count {
    font-size: 24px;
    font-weight: 600;
    color: #303133;
  }
  .grade-desc {
    color: #909399;
    font-size: 12px;
  }
}
</style>
