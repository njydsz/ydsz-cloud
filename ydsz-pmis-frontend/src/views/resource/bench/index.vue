<script setup lang="ts">
/**
 * Bench 闲置池管理
 *
 * 1) 仪表盘：累计闲置成本 + 各池人数 + 流动趋势
 * 2) 入池/出池操作
 * 3) 分页查询 + 筛选
 */
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
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

async function fetchPools() {
  try {
    const { data } = await pageResourcePools(1, 100, undefined, 'ACTIVE')
    pools.value = data.list || []
  } catch {
    pools.value = []
  }
}

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

const statusMap: Record<string, { label: string; type: 'warning' | 'info' }> = {
  ACTIVE: { label: '在池', type: 'warning' },
  EXITED: { label: '已出池', type: 'info' },
}
const reasonMap: Record<string, { label: string; color: string }> = {
  PROJECT_END: { label: '项目结束', color: '#909399' },
  RESERVE: { label: '储备', color: '#67c23a' },
  TRAINING: { label: '培训', color: '#409eff' },
  LEAVE: { label: '请假', color: '#e6a23c' },
}
const actionMap: Record<string, { label: string; type: 'warning' | 'success' }> = {
  ENTER: { label: '入池', type: 'warning' },
  EXIT: { label: '出池', type: 'success' },
}

function fmtMoney(n?: number) {
  if (n === undefined || n === null) return '-'
  return `¥${Number(n).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`
}

const topCostList = computed(() => {
  return [...list.value]
    .filter((r) => r.status === 'EXITED')
    .sort((a, b) => (b.totalIdleCost ?? 0) - (a.totalIdleCost ?? 0))
    .slice(0, 5)
})

function onQuery() {
  query.page = 1
  fetchList()
}
function onReset() {
  query.page = 1
  query.size = 10
  query.poolId = undefined
  query.status = ''
  fetchList()
}
function onPageChange() {
  fetchList()
}
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

const rules = {
  benchCode: [{ required: true, message: '请输入编号', trigger: 'blur' }],
  employeeId: [{ required: true, message: '请输入员工 ID', trigger: 'change' }],
  action: [{ required: true, message: '请选择动作', trigger: 'change' }],
  benchDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
}

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

async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  const pool = pools.value.find((p) => p.id === form.poolId)
  if (pool) form.remark = (form.remark || '') + (form.remark ? ' | ' : '') + `池：${pool.poolName}`
  await actBench(form)
  ElMessage.success(form.action === 'ENTER' ? '入池成功' : '出池成功')
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
          <span><el-icon><Coin /></el-icon> Bench 闲置池概览</span>
          <el-button link :icon="'Refresh'" @click="fetchDashboard">刷新</el-button>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :span="6">
          <div class="metric">
            <div class="label">累计闲置成本</div>
            <div class="value danger">{{ fmtMoney(totalCost) }}</div>
            <div class="hint">历史已出池成本总和</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric">
            <div class="label">当前在池人数</div>
            <div class="value warning">{{ poolStats.reduce((s, p: any) => s + (p.activeCount ?? p.count ?? 0), 0) }}</div>
            <div class="hint">所有池总在池</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric">
            <div class="label">池数量</div>
            <div class="value">{{ poolStats.length }}</div>
            <div class="hint">活跃池数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric">
            <div class="label">Top 成本</div>
            <div class="value-small">
              <div v-for="(r, i) in topCostList" :key="r.id" class="top-line">
                <span>{{ i + 1 }}. {{ r.employeeName || `#${r.employeeId}` }}</span>
                <span class="danger">{{ fmtMoney(r.totalIdleCost) }}</span>
              </div>
              <div v-if="!topCostList.length" class="hint">暂无</div>
            </div>
          </div>
        </el-col>
      </el-row>
      <!-- 池分布 -->
      <div v-if="poolStats.length" class="pool-bar">
        <div class="title">资源池分布</div>
        <div v-for="(p, i) in poolStats" :key="i" class="bar">
          <div class="bar-label">{{ p.poolName || `池 #${p.poolId}` }}</div>
          <el-progress
            :percentage="Math.min(100, Math.round(((p.activeCount ?? p.count ?? 0) / Math.max(1, list.filter(x => x.status === 'ACTIVE').length)) * 100))"
            :stroke-width="14"
            :text-inside="true"
            :format="() => `${p.activeCount ?? p.count ?? 0} 人`"
            :color="i % 2 === 0 ? '#409eff' : '#67c23a'"
          />
        </div>
      </div>
    </el-card>

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
        <el-form-item label="资源池">
          <el-select v-model="query.poolId" placeholder="全部" clearable style="width: 180px">
            <el-option
              v-for="p in pools"
              :key="p.id"
              :label="p.poolName"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="在池" value="ACTIVE" />
            <el-option label="已出池" value="EXITED" />
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
          入池
        </el-button>
        <el-button
          v-if="hasPerm(PC.RESOURCE_BENCH_OUT)"
          type="success"
          :icon="'Upload'"
          @click="openAct('EXIT')"
        >
          出池
        </el-button>
      </template>
      <template #table>
        <vxe-table :data="list" :loading="loading" border height="auto">
          <vxe-column field="benchCode" title="编号" width="160" />
          <vxe-column field="employeeName" title="员工" width="100">
            <template #default="{ row }">
              {{ row.employeeName || `#${row.employeeId}` }}
            </template>
          </vxe-column>
          <vxe-column field="levelCode" title="职级" width="80" />
          <vxe-column field="poolId" title="所属池" width="120">
            <template #default="{ row }">
              {{ pools.find(p => p.id === row.poolId)?.poolName || `池 #${row.poolId}` }}
            </template>
          </vxe-column>
          <vxe-column field="benchReason" title="动作" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.benchReason" :type="actionMap[row.benchReason]?.type || 'info'" size="small">
                {{ actionMap[row.benchReason]?.label || row.benchReason }}
              </el-tag>
            </template>
          </vxe-column>
          <vxe-column field="reasonType" title="原因" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.reasonType" :color="reasonMap[row.reasonType]?.color" effect="plain" size="small">
                {{ reasonMap[row.reasonType]?.label || row.reasonType }}
              </el-tag>
            </template>
          </vxe-column>
          <vxe-column field="benchDate" title="入池日期" width="120" />
          <vxe-column field="exitDate" title="出池日期" width="120" />
          <vxe-column field="idleDays" title="闲置天数" width="90" />
          <vxe-column field="dailyCost" title="日成本" width="110">
            <template #default="{ row }">
              <span class="num">{{ fmtMoney(row.dailyCost) }}</span>
            </template>
          </vxe-column>
          <vxe-column field="totalIdleCost" title="累计闲置成本" width="140">
            <template #default="{ row }">
              <span :class="['num', (row.totalIdleCost ?? 0) > 50000 ? 'danger' : '']">
                {{ fmtMoney(row.totalIdleCost) }}
              </span>
            </template>
          </vxe-column>
          <vxe-column field="status" title="状态" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.status" :type="statusMap[row.status]?.type || 'info'" size="small">
                {{ statusMap[row.status]?.label || row.status }}
              </el-tag>
            </template>
          </vxe-column>
          <vxe-column field="remark" title="备注" min-width="120" show-overflow />
          <template #empty><el-empty description="暂无 Bench 记录" /></template>
        </vxe-table>
      </template>
    </PageLayout>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogAction === 'ENTER' ? '员工入池' : '员工出池'"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="Bench 编号" prop="benchCode">
          <el-input v-model="form.benchCode" />
        </el-form-item>
        <el-form-item label="员工 ID" prop="employeeId">
          <el-input-number v-model="form.employeeId" :min="1" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item label="员工姓名">
          <el-input v-model="form.employeeName" />
        </el-form-item>
        <el-form-item label="职级">
          <el-input v-model="form.levelCode" placeholder="如 L5" />
        </el-form-item>
        <el-form-item label="目标池">
          <el-select v-model="form.poolId" placeholder="请选择" style="width: 100%">
            <el-option v-for="p in pools" :key="p.id" :label="p.poolName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作" prop="action">
          <el-radio-group v-model="form.action">
            <el-radio value="ENTER">入池</el-radio>
            <el-radio value="EXIT">出池</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="原因">
          <el-select v-model="form.reasonType" style="width: 200px">
            <el-option label="项目结束" value="PROJECT_END" />
            <el-option label="储备" value="RESERVE" />
            <el-option label="培训" value="TRAINING" />
            <el-option label="请假" value="LEAVE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="form.action === 'ENTER' ? '入池日期' : '出池日期'" prop="benchDate">
          <el-date-picker v-model="form.benchDate" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
        </el-form-item>
        <el-form-item v-if="form.action === 'ENTER'" label="日成本">
          <el-input-number v-model="form.dailyCost" :min="0" :precision="2" controls-position="right" style="width: 200px" />
          <span class="form-hint">未填则系统按职级费率自动计算</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="submit">确认</el-button>
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
