<!--
  @file EVM 挣值管理
  @description 挣值管理（Earned Value Management）独立页面，提供 EVM 测量记录的分页查询、
               录入/更新（按 initiation + wbs + period 幂等）、项目级健康仪表盘（CPI/SPI/EAC/VAC + 趋势）
               及红黄绿三色预警展示；CPI/SPI 由后端自动计算并判定预警级别。
  @module views/execution/evm
-->
<script setup lang="ts">
/**
 * EVM 挣值管理 - 独立页
 *
 * 功能：
 * 1) EVM 测量记录分页查询
 * 2) 录入/更新（按 initiation+wbs+period 幂等）
 * 3) 项目级健康仪表盘（CPI/SPI/EAC/VAC + 趋势）
 * 4) 预警条数与红黄绿三色展示
 */
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import {
  saveEvm,
  deleteEvm,
  getEvmDashboard,
  listEvmByInitiation,
} from '@/api/execution/evm'
import type { EvmMeasureVO, EvmMeasureCreateDTO, EvmDashboardVO } from '@/api/execution/evm'
import { PC } from '@/constants/permissionCodes'
import { useUserStore } from '@/store/modules/user'

/** 用户信息 store（用于权限判断） */
const userStore = useUserStore()
/** 权限判断快捷方法 */
const hasPerm = (code: string) => userStore.hasPermission(code)

/** 列表加载状态 */
const loading = ref(false)
/** EVM 测量记录列表 */
const list = ref<EvmMeasureVO[]>([])
/** 记录总数（分页用） */
const total = ref(0)
/** 查询条件：项目 ID + 预警级别 */
const query = reactive({
  page: 1,
  size: 10,
  initiationId: undefined as number | undefined,
  alertLevel: '',
})

/** 拉取 EVM 测量列表（前端按 alertLevel 过滤 + 分页，避免大改后端接口） */
async function fetchList() {
  if (!query.initiationId) {
    list.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const { data } = await listEvmByInitiation(query.initiationId)
    // 在前端再按 alertLevel 过滤 + 分页（简单实现，避免大改后端接口）
    let arr = data
    if (query.alertLevel) arr = arr.filter((x) => x.alertLevel === query.alertLevel)
    total.value = arr.length
    const start = (query.page - 1) * query.size
    list.value = arr.slice(start, start + query.size)
  } finally {
    loading.value = false
  }
}

/** 预警级别 → 标签/样式映射（GREEN/YELLOW/RED） */
const alertLevelMap: Record<string, { label: string; type: 'success' | 'warning' | 'danger' }> = {
  GREEN: { label: '健康', type: 'success' },
  YELLOW: { label: '预警', type: 'warning' },
  RED: { label: '严重', type: 'danger' },
}

/**
 * 数字格式化
 * @param n 待格式化数值
 * @param d 保留小数位（默认 2）
 */
function fmt(n?: number, d = 2) {
  if (n === undefined || n === null) return '-'
  return Number(n).toFixed(d)
}

/** 项目 EVM 健康仪表盘数据 */
const dashboard = ref<EvmDashboardVO | null>(null)
/** 仪表盘加载状态 */
const dashboardLoading = ref(false)
/** 拉取项目 EVM 健康仪表盘（CPI/SPI/EAC/VAC + 趋势） */
async function fetchDashboard() {
  if (!query.initiationId) {
    dashboard.value = null
    return
  }
  dashboardLoading.value = true
  try {
    const { data } = await getEvmDashboard(query.initiationId)
    dashboard.value = data
  } finally {
    dashboardLoading.value = false
  }
}

/** 触发查询：重置页码并并行刷新列表与仪表盘 */
async function onQuery() {
  query.page = 1
  await fetchList()
  await fetchDashboard()
}

/** 重置查询条件并刷新列表 */
function onReset() {
  query.page = 1
  query.size = 10
  query.initiationId = undefined
  query.alertLevel = ''
  fetchList()
}

/** 翻页回调 */
function onPageChange() {
  fetchList()
}

/** 刷新：并行重新加载列表与仪表盘 */
async function onRefresh() {
  await fetchList()
  await fetchDashboard()
}

// ============= 录入弹窗 =============
/** 提交按钮 loading 状态，防止重复提交 */
const submitting = ref(false)
/** 录入/编辑弹窗可见性 */
const dialogVisible = ref(false)
/** 表单引用（用于校验） */
const formRef = ref<any>()
/** EVM 测量录入/编辑表单 */
const form = reactive<EvmMeasureCreateDTO>({
  initiationId: 0,
  wbsTaskId: undefined,
  period: '',
  pv: 0,
  ev: 0,
  ac: 0,
  bac: 0,
  measureDate: '',
  remark: '',
})
/** 当前编辑记录 ID（null 表示新增） */
const editingId = ref<number | null>(null)

/** 表单校验规则 */
const rules = {
  initiationId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  period: [{ required: true, message: '请输入周期', trigger: 'blur' }],
  pv: [{ required: true, message: '请输入 PV', trigger: 'blur' }],
  ev: [{ required: true, message: '请输入 EV', trigger: 'blur' }],
  ac: [{ required: true, message: '请输入 AC', trigger: 'blur' }],
  bac: [{ required: true, message: '请输入 BAC', trigger: 'blur' }],
}

/** 打开新增弹窗，默认周期/测量日期为当日 */
function openCreate() {
  editingId.value = null
  Object.assign(form, {
    initiationId: query.initiationId || 0,
    wbsTaskId: undefined,
    period: new Date().toISOString().slice(0, 7),
    pv: 0,
    ev: 0,
    ac: 0,
    bac: 0,
    measureDate: new Date().toISOString().slice(0, 10),
    remark: '',
  })
  dialogVisible.value = true
}

/**
 * 打开编辑弹窗，回填当前行数据
 * @param row EVM 测量记录
 */
function openEdit(row: EvmMeasureVO) {
  editingId.value = row.id ?? null
  Object.assign(form, {
    initiationId: row.initiationId,
    wbsTaskId: row.wbsTaskId,
    period: row.period,
    pv: row.pv,
    ev: row.ev,
    ac: row.ac,
    bac: row.bac,
    measureDate: row.measureDate || '',
    remark: row.remark || '',
  })
  dialogVisible.value = true
}

/** 保存（新增或更新），后端自动重算 CPI/SPI 并判定预警级别 */
async function submit() {
  if (!formRef.value) return
  try {
    submitting.value = true
    await formRef.value.validate()
    await saveEvm(form)
    ElMessage.success('保存成功，CPI/SPI 已自动重算并判定预警级别')
    dialogVisible.value = false
    onRefresh()
  } catch {
    // 校验或保存失败，保持弹窗打开
  } finally {
    submitting.value = false
  }
}

/**
 * 删除指定 EVM 测量记录
 * @param row EVM 测量记录
 */
async function onDelete(row: EvmMeasureVO) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(`确认删除周期 ${row.period} 的 EVM 测量记录？`, '提示', {
      type: 'warning',
    })
    await deleteEvm(row.id)
    ElMessage.success('删除成功')
    onRefresh()
  } catch {
    // 用户取消
  }
}

// 简易趋势：用 CSS 柱状图展示 CPI / SPI 偏离 1.0 的程度
/** 趋势柱状图数据（CPI/SPI 偏差百分点 + EAC/VAC） */
const trendBars = computed(() => {
  if (!dashboard.value?.trend) return []
  return dashboard.value.trend.map((t) => ({
    period: t.period,
    cpiGap: ((t.cpi ?? 1) - 1) * 100, // 偏差百分点
    spiGap: ((t.spi ?? 1) - 1) * 100,
    eac: t.eac,
    vac: t.vac,
  }))
})

/** 页面挂载时若有项目 ID 则加载数据 */
onMounted(() => {
  if (query.initiationId) {
    fetchList()
    fetchDashboard()
  }
})
</script>

<template>
  <div class="evm-page">
    <!-- 项目健康仪表盘 -->
    <el-card v-if="query.initiationId" shadow="never" class="dashboard-card">
      <template #header>
        <div class="card-header">
          <span><el-icon><DataLine /></el-icon> 项目 EVM 健康仪表盘</span>
          <el-button
            v-if="hasPerm(PC.EXECUTION_EVM_DASHBOARD)"
            link
            :icon="'Refresh'"
            :loading="dashboardLoading"
            @click="fetchDashboard"
          >
            刷新
          </el-button>
        </div>
      </template>
      <el-row v-if="dashboard" :gutter="16" class="metric-row">
        <el-col :span="6">
          <div class="metric">
            <div class="label">平均 CPI</div>
            <div class="value" :class="{ danger: (dashboard.avgCpi ?? 1) < 0.95, warning: (dashboard.avgCpi ?? 1) >= 0.95 && (dashboard.avgCpi ?? 1) < 1 }">
              {{ fmt(dashboard.avgCpi, 3) }}
            </div>
            <div class="hint">EV / AC</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric">
            <div class="label">平均 SPI</div>
            <div class="value" :class="{ danger: (dashboard.avgSpi ?? 1) < 0.95, warning: (dashboard.avgSpi ?? 1) >= 0.95 && (dashboard.avgSpi ?? 1) < 1 }">
              {{ fmt(dashboard.avgSpi, 3) }}
            </div>
            <div class="hint">EV / PV</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric">
            <div class="label">最新 EAC</div>
            <div class="value">{{ fmt(dashboard.latestEac) }}</div>
            <div class="hint">完工估算</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric">
            <div class="label">最新 VAC</div>
            <div class="value" :class="{ danger: (dashboard.latestVac ?? 0) < 0, success: (dashboard.latestVac ?? 0) >= 0 }">
              {{ fmt(dashboard.latestVac) }}
            </div>
            <div class="hint">完工偏差 (BAC-EAC)</div>
          </div>
        </el-col>
      </el-row>
      <el-empty v-else description="暂无 EVM 测量数据，请先录入" />

      <!-- 趋势图 -->
      <div v-if="trendBars.length" class="trend">
        <div class="trend-title">CPI/SPI 偏差趋势（百分比，0 为达标）</div>
        <div class="trend-bars">
          <div v-for="b in trendBars" :key="b.period" class="trend-bar">
            <div class="bar-pair">
              <div
                class="bar cpi"
                :style="{ height: `${Math.min(Math.abs(b.cpiGap), 50)}px`, background: b.cpiGap >= 0 ? '#67c23a' : '#f56c6c' }"
                :title="`CPI 偏差 ${b.cpiGap.toFixed(2)}%`"
              ></div>
              <div
                class="bar spi"
                :style="{ height: `${Math.min(Math.abs(b.spiGap), 50)}px`, background: b.spiGap >= 0 ? '#409eff' : '#e6a23c' }"
                :title="`SPI 偏差 ${b.spiGap.toFixed(2)}%`"
              ></div>
            </div>
            <div class="bar-label">{{ b.period }}</div>
          </div>
        </div>
        <div class="legend">
          <span><i class="dot" style="background: #67c23a" /> CPI 健康</span>
          <span><i class="dot" style="background: #f56c6c" /> CPI 预警</span>
          <span><i class="dot" style="background: #409eff" /> SPI 健康</span>
          <span><i class="dot" style="background: #e6a23c" /> SPI 预警</span>
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
      <!-- 搜索栏 -->
      <template #search>
        <el-form-item label="项目 ID">
          <el-input-number
            v-model="query.initiationId"
            :min="1"
            placeholder="请输入项目 ID"
            controls-position="right"
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="预警级别">
          <el-select v-model="query.alertLevel" placeholder="全部" clearable style="width: 130px">
            <el-option label="健康" value="GREEN" />
            <el-option label="预警" value="YELLOW" />
            <el-option label="严重" value="RED" />
          </el-select>
        </el-form-item>
      </template>
      <!-- 工具栏 -->
      <template #toolbar>
        <el-button
          v-if="hasPerm(PC.EXECUTION_EVM_SAVE)"
          type="primary"
          :icon="'Plus'"
          :disabled="!query.initiationId"
          @click="openCreate"
        >
          录入 EVM
        </el-button>
      </template>
      <!-- EVM 测量列表表格 -->
      <template #table="scope">
        <vxe-table :data="list" :loading="loading" border height="auto" :scroll-y="scope.tableProps.scrollY">
          <vxe-column field="period" title="周期" width="100" />
          <vxe-column field="pv" title="PV" width="110">
            <template #default="{ row }">
              <span class="num">{{ fmt(row.pv) }}</span>
            </template>
          </vxe-column>
          <vxe-column field="ev" title="EV" width="110">
            <template #default="{ row }">
              <span class="num">{{ fmt(row.ev) }}</span>
            </template>
          </vxe-column>
          <vxe-column field="ac" title="AC" width="110">
            <template #default="{ row }">
              <span class="num">{{ fmt(row.ac) }}</span>
            </template>
          </vxe-column>
          <vxe-column field="bac" title="BAC" width="110">
            <template #default="{ row }">
              <span class="num">{{ fmt(row.bac) }}</span>
            </template>
          </vxe-column>
          <vxe-column field="cpi" title="CPI" width="90">
            <template #default="{ row }">
              <span :class="['num', (row.cpi ?? 1) < 0.95 ? 'danger' : (row.cpi ?? 1) < 1 ? 'warning' : 'success']">
                {{ fmt(row.cpi, 3) }}
              </span>
            </template>
          </vxe-column>
          <vxe-column field="spi" title="SPI" width="90">
            <template #default="{ row }">
              <span :class="['num', (row.spi ?? 1) < 0.95 ? 'danger' : (row.spi ?? 1) < 1 ? 'warning' : 'success']">
                {{ fmt(row.spi, 3) }}
              </span>
            </template>
          </vxe-column>
          <vxe-column field="cv" title="CV" width="100">
            <template #default="{ row }">
              <span :class="['num', (row.cv ?? 0) < 0 ? 'danger' : 'success']">
                {{ fmt(row.cv) }}
              </span>
            </template>
          </vxe-column>
          <vxe-column field="sv" title="SV" width="100">
            <template #default="{ row }">
              <span :class="['num', (row.sv ?? 0) < 0 ? 'danger' : 'success']">
                {{ fmt(row.sv) }}
              </span>
            </template>
          </vxe-column>
          <vxe-column field="eac" title="EAC" width="110">
            <template #default="{ row }">
              <span class="num">{{ fmt(row.eac) }}</span>
            </template>
          </vxe-column>
          <vxe-column field="vac" title="VAC" width="110">
            <template #default="{ row }">
              <span :class="['num', (row.vac ?? 0) < 0 ? 'danger' : 'success']">
                {{ fmt(row.vac) }}
              </span>
            </template>
          </vxe-column>
          <vxe-column field="alertLevel" title="预警" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.alertLevel" :type="alertLevelMap[row.alertLevel]?.type || 'info'" size="small">
                {{ alertLevelMap[row.alertLevel]?.label || row.alertLevel }}
              </el-tag>
            </template>
          </vxe-column>
          <vxe-column field="alertReason" title="预警原因" min-width="180" show-overflow />
          <vxe-column field="measureDate" title="测量日期" width="120" />
          <vxe-column field="remark" title="备注" min-width="120" show-overflow />
          <vxe-column title="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="hasPerm(PC.EXECUTION_EVM_SAVE)"
                link
                type="primary"
                @click="openEdit(row)"
              >编辑</el-button>
              <el-button
                v-if="hasPerm(PC.EXECUTION_EVM_SAVE)"
                link
                type="danger"
                @click="onDelete(row)"
              >删除</el-button>
            </template>
          </vxe-column>
          <template #empty>
            <el-empty :description="query.initiationId ? '该项目暂无 EVM 测量数据' : '请先选择项目 ID 后查询'" />
          </template>
        </vxe-table>
      </template>
    </PageLayout>

    <!-- 录入/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑 EVM 测量' : '录入 EVM 测量'"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="项目 ID" prop="initiationId">
          <el-input-number v-model="form.initiationId" :min="1" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item label="WBS 任务 ID" prop="wbsTaskId">
          <el-input-number v-model="form.wbsTaskId" :min="1" controls-position="right" style="width: 200px" placeholder="可空：项目级度量" />
        </el-form-item>
        <el-form-item label="周期 (YYYY-MM)" prop="period">
          <el-input v-model="form.period" placeholder="例如 2026-07" maxlength="7" />
        </el-form-item>
        <el-form-item label="测量日期">
          <el-date-picker v-model="form.measureDate" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
        </el-form-item>
        <el-divider content-position="left">三量与 BAC</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="PV 计划值" prop="pv">
              <el-input-number v-model="form.pv" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="EV 挣值" prop="ev">
              <el-input-number v-model="form.ev" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="AC 实际成本" prop="ac">
              <el-input-number v-model="form.ac" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="BAC 完工预算" prop="bac">
              <el-input-number v-model="form.bac" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="系统将自动计算 CPI / SPI / EAC / VAC / ETC / TCPI 并判定预警级别（黄/红）"
        />
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.evm-page {
  .dashboard-card {
    margin-bottom: 16px;
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-weight: 600;
    }
    .metric-row .metric {
      text-align: center;
      padding: 12px 0;
      .label { font-size: 12px; color: #909399; }
      .value {
        font-size: 24px;
        font-weight: 600;
        color: #303133;
        margin: 4px 0;
        &.danger { color: #f56c6c; }
        &.warning { color: #e6a23c; }
        &.success { color: #67c23a; }
      }
      .hint { font-size: 11px; color: #c0c4cc; }
    }
  }
  .trend {
    margin-top: 16px;
    .trend-title { font-size: 13px; color: #606266; margin-bottom: 12px; }
    .trend-bars {
      display: flex;
      gap: 12px;
      align-items: flex-end;
      height: 80px;
      padding: 8px 0;
      border-bottom: 1px solid #ebeef5;
    }
    .trend-bar { display: flex; flex-direction: column; align-items: center; gap: 4px; }
    .bar-pair { display: flex; gap: 2px; align-items: flex-end; height: 50px; }
    .bar { width: 8px; border-radius: 2px 2px 0 0; }
    .bar-label { font-size: 11px; color: #909399; }
    .legend {
      display: flex; gap: 16px; font-size: 12px; color: #606266; margin-top: 8px;
      .dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 4px; }
    }
  }
  .num { font-variant-numeric: tabular-nums; }
  .danger { color: #f56c6c; }
  .warning { color: #e6a23c; }
  .success { color: #67c23a; }
}
</style>
