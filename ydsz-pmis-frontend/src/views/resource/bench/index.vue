<!--
  @file Bench 闲置池管理
  @description Bench 闲置池管理页面：提供闲置概览仪表盘（累计闲置成本、各池人数、Top 成本与池分布）、员工入池/出池操作以及分页筛选查询。闲置天数由后端 ChronoUnit.DAYS.between 计算，培训窗口为 30 天。对应路由 /resource/bench，后端服务 ydsz-pmis-userinfo（端口 9002）。
  @module views/resource/bench
-->
<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import PageLayout from '@/components/common/PageLayout.vue'
import {
  pageBench,
  actBench,
  benchDashboard,
  aggregateByPool,
  totalIdleCost,
} from '@/api/resource/bench'
import type { BenchRecordVO, BenchRecordCreateDTO, BenchDashboardVO } from '@/api/resource/bench'
import { pageResourcePools } from '@/api/resource/pool'
import type { ResourcePoolVO } from '@/api/resource/pool/types'
import { PC } from '@/constants/permissionCodes'
import { useUserStore } from '@/store/modules/user'

const { t } = useI18n()
const userStore = useUserStore()
const hasPerm = (code: string) => userStore.hasPermission(code)

const loading = ref(false)
const list = ref<BenchRecordVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  poolId: undefined as number | undefined,
  status: '',
})

const dashboard = ref<BenchDashboardVO | null>(null)
const totalCost = ref(0)
const poolStats = ref<Array<Record<string, any>>>([])
const pools = ref<ResourcePoolVO[]>([])

/** 拉取全部启用状态的资源池（用于下拉选项） */
async function fetchPools() {
  try {
    const { data } = await pageResourcePools(1, 100, undefined, 'ACTIVE')
    pools.value = data.list || []
  } catch {
    pools.value = []
  }
}

/** 拉取 Bench 记录分页列表 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageBench(query.page, query.size, {
      poolId: query.poolId,
      status: query.status || undefined,
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 拉取闲置池仪表盘数据：概览、池分布、累计闲置成本 */
async function fetchDashboard() {
  try {
    const { data } = await benchDashboard()
    dashboard.value = data
  } catch {
    dashboard.value = null
  }
  try {
    const { data } = await aggregateByPool()
    poolStats.value = data || []
  } catch {
    poolStats.value = []
  }
  try {
    const { data } = await totalIdleCost()
    totalCost.value = Number(data || 0)
  } catch {
    totalCost.value = 0
  }
}

const statusMap = computed<Record<string, { label: string; type: 'warning' | 'info' }>>(() => ({
  ACTIVE: { label: t('resource.bench.status.ACTIVE'), type: 'warning' },
  EXITED: { label: t('resource.bench.status.EXITED'), type: 'info' },
}))
const reasonMap = computed<Record<string, { label: string; color: string }>>(() => ({
  PROJECT_END: { label: t('resource.bench.reason.PROJECT_END'), color: '#909399' },
  RESERVE: { label: t('resource.bench.reason.RESERVE'), color: '#67c23a' },
  TRAINING: { label: t('resource.bench.reason.TRAINING'), color: '#409eff' },
  LEAVE: { label: t('resource.bench.reason.LEAVE'), color: '#e6a23c' },
}))
const actionMap = computed<Record<string, { label: string; type: 'warning' | 'success' }>>(() => ({
  ENTER: { label: t('resource.bench.action.ENTER'), type: 'warning' },
  EXIT: { label: t('resource.bench.action.EXIT'), type: 'success' },
}))

/**
 * 金额格式化（数字 → ¥1,234.56）
 * @param n 金额数值
 * @returns 格式化后的字符串，空值返回 '-'
 */
function fmtMoney(n?: number) {
  if (n === undefined || n === null) return '-'
  return `¥${Number(n).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`
}

// 已出池记录中累计闲置成本 Top5（仪表盘展示用）
const topCostList = computed(() => {
  return [...list.value]
    .filter((r) => r.status === 'EXITED')
    .sort((a, b) => (b.totalIdleCost ?? 0) - (a.totalIdleCost ?? 0))
    .slice(0, 5)
})

/** 点击查询：重置页码到第 1 页后拉取列表 */
function onQuery() {
  query.page = 1
  fetchList()
}
/** 重置查询条件并刷新列表 */
function onReset() {
  query.page = 1
  query.size = 10
  query.poolId = undefined
  query.status = ''
  fetchList()
}
/** 分页变化时刷新列表 */
function onPageChange() {
  fetchList()
}
/** 刷新：同时拉取列表与仪表盘数据 */
async function onRefresh() {
  await fetchList()
  await fetchDashboard()
}

// 入池/出池 弹窗
const dialogVisible = ref(false)
const dialogAction = ref<'ENTER' | 'EXIT'>('ENTER')
const formRef = ref<any>()
const form = reactive<BenchRecordCreateDTO>({
  benchCode: '',
  employeeId: 0,
  employeeName: '',
  levelCode: '',
  poolId: undefined,
  action: 'ENTER',
  reasonType: 'PROJECT_END',
  benchDate: new Date().toISOString().slice(0, 10),
  dailyCost: 0,
  remark: '',
})

const rules = computed(() => ({
  benchCode: [{ required: true, message: t('resource.bench.rules.codeRequired'), trigger: 'blur' }],
  employeeId: [{ required: true, message: t('resource.bench.rules.employeeIdRequired'), trigger: 'change' }],
  action: [{ required: true, message: t('resource.bench.rules.actionRequired'), trigger: 'change' }],
  benchDate: [{ required: true, message: t('resource.bench.rules.dateRequired'), trigger: 'change' }],
}))

/**
 * 打开入池/出池弹窗，按动作类型初始化表单默认值
 * @param action 动作类型：ENTER 入池 / EXIT 出池
 */
function openAct(action: 'ENTER' | 'EXIT') {
  dialogAction.value = action
  Object.assign(form, {
    benchCode: `BENCH-${action}-${Date.now()}`,
    employeeId: 0,
    employeeName: '',
    levelCode: '',
    poolId: pools.value[0]?.id,
    action,
    reasonType: 'PROJECT_END',
    benchDate: new Date().toISOString().slice(0, 10),
    dailyCost: 0,
    remark: '',
  })
  dialogVisible.value = true
}

/** 提交入池/出池动作，成功后刷新列表与仪表盘 */
async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  const pool = pools.value.find((p) => p.id === form.poolId)
  if (pool) form.remark = (form.remark || '') + (form.remark ? ' | ' : '') + t('resource.bench.messages.poolSuffix', { name: pool.poolName })
  await actBench(form)
  ElMessage.success(form.action === 'ENTER' ? t('resource.bench.messages.enterSuccess') : t('resource.bench.messages.exitSuccess'))
  dialogVisible.value = false
  fetchList()
  fetchDashboard()
}

onMounted(async () => {
  await fetchPools()
  await Promise.all([fetchList(), fetchDashboard()])
})
</script>

<template>
  <div class="bench-page">
    <!-- 仪表盘 -->
    <el-card shadow="never" class="dashboard-card">
      <template #header>
        <div class="card-header">
          <span><el-icon><Coin /></el-icon> {{ t('resource.bench.dashboard.title') }}</span>
          <el-button link :icon="'Refresh'" @click="fetchDashboard">{{ t('resource.bench.buttons.refresh') }}</el-button>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :span="6">
          <div class="metric">
            <div class="label">{{ t('resource.bench.dashboard.totalCost') }}</div>
            <div class="value danger">{{ fmtMoney(totalCost) }}</div>
            <div class="hint">{{ t('resource.bench.dashboard.totalCostHint') }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric">
            <div class="label">{{ t('resource.bench.dashboard.activeCount') }}</div>
            <div class="value warning">{{ poolStats.reduce((s, p: any) => s + (p.activeCount ?? p.count ?? 0), 0) }}</div>
            <div class="hint">{{ t('resource.bench.dashboard.activeCountHint') }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric">
            <div class="label">{{ t('resource.bench.dashboard.poolCount') }}</div>
            <div class="value">{{ poolStats.length }}</div>
            <div class="hint">{{ t('resource.bench.dashboard.poolCountHint') }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric">
            <div class="label">{{ t('resource.bench.dashboard.topCost') }}</div>
            <div class="value-small">
              <div v-for="(r, i) in topCostList" :key="r.id" class="top-line">
                <span>{{ i + 1 }}. {{ r.employeeName || `#${r.employeeId}` }}</span>
                <span class="danger">{{ fmtMoney(r.totalIdleCost) }}</span>
              </div>
              <div v-if="!topCostList.length" class="hint">{{ t('resource.bench.dashboard.topCostEmpty') }}</div>
            </div>
          </div>
        </el-col>
      </el-row>
      <!-- 池分布 -->
      <div v-if="poolStats.length" class="pool-bar">
        <div class="title">{{ t('resource.bench.dashboard.poolDistTitle') }}</div>
        <div v-for="(p, i) in poolStats" :key="i" class="bar">
          <div class="bar-label">{{ p.poolName || t('resource.bench.dashboard.poolFallback', { id: p.poolId }) }}</div>
          <el-progress
            :percentage="Math.min(100, Math.round(((p.activeCount ?? p.count ?? 0) / Math.max(1, list.filter(x => x.status === 'ACTIVE').length)) * 100))"
            :stroke-width="14"
            :text-inside="true"
            :format="() => t('resource.bench.dashboard.poolUnit', { n: p.activeCount ?? p.count ?? 0 })"
            :color="i % 2 === 0 ? '#409eff' : '#67c23a'"
          />
        </div>
      </div>
    </el-card>

    <!-- 列表查询与入池/出池操作 -->
    <PageLayout
      :query="query"
      :list="list"
      :total="total"
      :loading="loading"
      @query="onQuery"
      @reset="onReset"
      @page-change="onPageChange"
      @refresh="onRefresh"
    >
      <template #search>
        <el-form-item :label="t('resource.bench.search.pool')">
          <el-select v-model="query.poolId" :placeholder="t('common.all')" clearable style="width: 180px">
            <el-option
              v-for="p in pools"
              :key="p.id"
              :label="p.poolName"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('resource.bench.search.status')">
          <el-select v-model="query.status" :placeholder="t('common.all')" clearable style="width: 120px">
            <el-option :label="t('resource.bench.status.ACTIVE')" value="ACTIVE" />
            <el-option :label="t('resource.bench.status.EXITED')" value="EXITED" />
          </el-select>
        </el-form-item>
      </template>
      <template #toolbar>
        <el-button
          v-if="hasPerm(PC.RESOURCE_BENCH_INTO)"
          type="warning"
          :icon="'Download'"
          @click="openAct('ENTER')"
        >
          {{ t('resource.bench.buttons.enter') }}
        </el-button>
        <el-button
          v-if="hasPerm(PC.RESOURCE_BENCH_OUT)"
          type="success"
          :icon="'Upload'"
          @click="openAct('EXIT')"
        >
          {{ t('resource.bench.buttons.exit') }}
        </el-button>
      </template>
      <template #table>
        <vxe-table :data="list" :loading="loading" border height="auto">
          <vxe-column field="benchCode" :title="t('resource.bench.columns.code')" width="160" />
          <vxe-column field="employeeName" :title="t('resource.bench.columns.employee')" width="100">
            <template #default="{ row }">
              {{ row.employeeName || `#${row.employeeId}` }}
            </template>
          </vxe-column>
          <vxe-column field="levelCode" :title="t('resource.bench.columns.levelCode')" width="80" />
          <vxe-column field="poolId" :title="t('resource.bench.columns.pool')" width="120">
            <template #default="{ row }">
              {{ pools.find(p => p.id === row.poolId)?.poolName || t('resource.bench.dashboard.poolFallback', { id: row.poolId }) }}
            </template>
          </vxe-column>
          <vxe-column field="benchReason" :title="t('resource.bench.columns.action')" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.benchReason" :type="actionMap[row.benchReason]?.type || 'info'" size="small">
                {{ actionMap[row.benchReason]?.label || row.benchReason }}
              </el-tag>
            </template>
          </vxe-column>
          <vxe-column field="reasonType" :title="t('resource.bench.columns.reason')" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.reasonType" :color="reasonMap[row.reasonType]?.color" effect="plain" size="small">
                {{ reasonMap[row.reasonType]?.label || row.reasonType }}
              </el-tag>
            </template>
          </vxe-column>
          <vxe-column field="benchDate" :title="t('resource.bench.columns.benchDate')" width="120" />
          <vxe-column field="exitDate" :title="t('resource.bench.columns.exitDate')" width="120" />
          <vxe-column field="idleDays" :title="t('resource.bench.columns.idleDays')" width="90" />
          <vxe-column field="dailyCost" :title="t('resource.bench.columns.dailyCost')" width="110">
            <template #default="{ row }">
              <span class="num">{{ fmtMoney(row.dailyCost) }}</span>
            </template>
          </vxe-column>
          <vxe-column field="totalIdleCost" :title="t('resource.bench.columns.totalIdleCost')" width="140">
            <template #default="{ row }">
              <span :class="['num', (row.totalIdleCost ?? 0) > 50000 ? 'danger' : '']">
                {{ fmtMoney(row.totalIdleCost) }}
              </span>
            </template>
          </vxe-column>
          <vxe-column field="status" :title="t('resource.bench.columns.status')" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.status" :type="statusMap[row.status]?.type || 'info'" size="small">
                {{ statusMap[row.status]?.label || row.status }}
              </el-tag>
            </template>
          </vxe-column>
          <vxe-column field="remark" :title="t('resource.bench.columns.remark')" min-width="120" show-overflow />
          <template #empty><el-empty :description="t('resource.bench.empty')" /></template>
        </vxe-table>
      </template>
    </PageLayout>

    <!-- 入池/出池弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogAction === 'ENTER' ? t('resource.bench.dialog.enterTitle') : t('resource.bench.dialog.exitTitle')"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item :label="t('resource.bench.form.code')" prop="benchCode">
          <el-input v-model="form.benchCode" />
        </el-form-item>
        <el-form-item :label="t('resource.bench.form.employeeId')" prop="employeeId">
          <el-input-number v-model="form.employeeId" :min="1" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item :label="t('resource.bench.form.employeeName')">
          <el-input v-model="form.employeeName" />
        </el-form-item>
        <el-form-item :label="t('resource.bench.form.levelCode')">
          <el-input v-model="form.levelCode" :placeholder="t('resource.bench.form.levelCodePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('resource.bench.form.targetPool')">
          <el-select v-model="form.poolId" :placeholder="t('resource.bench.dialog.targetPoolPlaceholder')" style="width: 100%">
            <el-option v-for="p in pools" :key="p.id" :label="p.poolName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('resource.bench.form.action')" prop="action">
          <el-radio-group v-model="form.action">
            <el-radio value="ENTER">{{ t('resource.bench.action.ENTER') }}</el-radio>
            <el-radio value="EXIT">{{ t('resource.bench.action.EXIT') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('resource.bench.form.reason')">
          <el-select v-model="form.reasonType" style="width: 200px">
            <el-option :label="t('resource.bench.reason.PROJECT_END')" value="PROJECT_END" />
            <el-option :label="t('resource.bench.reason.RESERVE')" value="RESERVE" />
            <el-option :label="t('resource.bench.reason.TRAINING')" value="TRAINING" />
            <el-option :label="t('resource.bench.reason.LEAVE')" value="LEAVE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="form.action === 'ENTER' ? t('resource.bench.form.benchDate') : t('resource.bench.form.exitDate')" prop="benchDate">
          <el-date-picker v-model="form.benchDate" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
        </el-form-item>
        <el-form-item v-if="form.action === 'ENTER'" :label="t('resource.bench.form.dailyCost')">
          <el-input-number v-model="form.dailyCost" :min="0" :precision="2" controls-position="right" style="width: 200px" />
          <span class="form-hint">{{ t('resource.bench.form.dailyCostHint') }}</span>
        </el-form-item>
        <el-form-item :label="t('resource.bench.form.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="loading" @click="submit">{{ t('resource.bench.footer.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.bench-page {
  .dashboard-card {
    margin-bottom: 16px;
    .card-header {
      display: flex; justify-content: space-between; align-items: center; font-weight: 600;
    }
    .metric {
      text-align: center;
      padding: 12px 0;
      .label { font-size: 12px; color: #909399; }
      .value {
        font-size: 24px; font-weight: 600; color: #303133; margin: 4px 0;
        &.danger { color: #f56c6c; }
        &.warning { color: #e6a23c; }
        &.success { color: #67c23a; }
      }
      .value-small { margin-top: 4px; }
      .top-line { display: flex; justify-content: space-between; font-size: 12px; margin: 2px 0; }
      .hint { font-size: 11px; color: #c0c4cc; }
    }
    .pool-bar {
      margin-top: 16px;
      .title { font-size: 13px; color: #606266; margin-bottom: 12px; }
      .bar { margin-bottom: 8px; }
      .bar-label { font-size: 12px; color: #606266; margin-bottom: 4px; }
    }
  }
  .num { font-variant-numeric: tabular-nums; }
  .danger { color: #f56c6c; }
  .warning { color: #e6a23c; }
  .form-hint { margin-left: 12px; color: #909399; font-size: 12px; }
}
</style>
