<!--
  @file 利润模拟
  @description 利润测算版本管理页面：支持测算版本分页查询、多版本对比(V1/V2/V3)、状态流转(DRAFT→SUBMITTED→APPROVED/REJECTED)，对应路由 /execution/profit-simulation
  @module views/execution/profit-simulation
-->
<script setup lang="ts">
/**
 * 利润测算 (Profit Simulation) 管理
 *
 * 1) 测算版本分页查询
 * 2) 多版本对比（V1/V2/V3）
 * 3) 状态流转：DRAFT -> SUBMITTED -> APPROVED/REJECTED
 */
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import {
  pageProfitSimulations,
  createProfitSimulation,
  changeSimulationStatus,
  deleteProfitSimulation,
  compareSimulations,
} from '@/api/execution/profit-simulation'
import type {
  ProfitSimulationVO,
  ProfitSimulationCreateDTO,
} from '@/api/execution/profit-simulation'
import { isHandledError } from '@/utils/error'
import { PC } from '@/constants/permissionCodes'
import { useUserStore } from '@/store/modules/user'

// 权限助手：统一通过 userStore 校验按钮级权限
const userStore = useUserStore()
const hasPerm = (code: string) => userStore.hasPermission(code)

// 列表查询状态
const loading = ref(false)
const list = ref<ProfitSimulationVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  initiationId: undefined as number | undefined,
  scenarioType: '',
  status: '',
})

/** 拉取测算版本分页数据，未选择项目时清空列表 */
async function fetchList() {
  if (!query.initiationId) {
    list.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const { data } = await pageProfitSimulations(query.page, query.size, {
      initiationId: query.initiationId,
      scenarioType: query.scenarioType || undefined,
      status: query.status || undefined,
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 状态字典：映射测算版本状态到标签文案与色值
const statusMap: Record<string, { label: string; type: 'info' | 'primary' | 'warning' | 'success' | 'danger' }> = {
  DRAFT: { label: '草稿', type: 'info' },
  SUBMITTED: { label: '待审批', type: 'primary' },
  APPROVED: { label: '已批准', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
}
// 场景字典：基准/乐观/悲观/自定义，用于对比卡片与表格标签配色
const scenarioMap: Record<string, { label: string; color: string }> = {
  BASE: { label: '基准', color: '#409eff' },
  OPTIMISTIC: { label: '乐观', color: '#67c23a' },
  PESSIMISTIC: { label: '悲观', color: '#f56c6c' },
  CUSTOM: { label: '自定义', color: '#909399' },
}

/** 金额格式化：千分位 + ¥ 前缀，空值返回 - */
function fmtMoney(n?: number) {
  if (n === undefined || n === null) return '-'
  return `¥${Number(n).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`
}
/** 百分比格式化：入参为小数(0.2 → 20.0%)，空值返回 - */
function fmtPct(n?: number) {
  if (n === undefined || n === null) return '-'
  return `${(Number(n) * 100).toFixed(1)}%`
}

/** 点击查询：重置页码后同步拉取列表与对比数据 */
function onQuery() {
  query.page = 1
  fetchList()
  fetchCompare()
}
/** 重置查询条件并刷新列表 */
function onReset() {
  query.page = 1
  query.size = 10
  query.initiationId = undefined
  query.scenarioType = ''
  query.status = ''
  fetchList()
}
/** 翻页回调 */
function onPageChange() {
  fetchList()
}
/** 手动刷新：重新拉取列表与对比数据 */
async function onRefresh() {
  await fetchList()
  await fetchCompare()
}

// 多版本对比
const compareData = ref<Array<Record<string, any>>>([])
async function fetchCompare() {
  if (!query.initiationId) {
    compareData.value = []
    return
  }
  try {
    const { data } = await compareSimulations(query.initiationId)
    compareData.value = data || []
  } catch (e) {
    compareData.value = []
    if (!isHandledError(e)) {
      ElMessage.error('版本对比数据加载失败，请刷新重试')
    }
  }
}

/** 提交按钮 loading 状态，防止重复提交 */
const submitting = ref(false)
// 弹窗 - 新建
const dialogVisible = ref(false)
const formRef = ref<any>()
const form = reactive<ProfitSimulationCreateDTO>({
  simulationCode: '',
  simulationName: '',
  initiationId: 0,
  scenarioType: 'BASE',
  contractAmount: 0,
  assumptions: '',
  targetMargin: 0.2,
  remark: '',
})

const rules = {
  simulationCode: [{ required: true, message: '请输入测算编号', trigger: 'blur' }],
  simulationName: [{ required: true, message: '请输入测算名称', trigger: 'blur' }],
  initiationId: [{ required: true, message: '请输入项目 ID', trigger: 'change' }],
  contractAmount: [{ required: true, message: '请输入合同金额', trigger: 'blur' }],
  targetMargin: [{ required: true, message: '请输入目标毛利率', trigger: 'blur' }],
}

function openCreate() {
  Object.assign(form, {
    simulationCode: `SIM-${Date.now()}`,
    simulationName: '',
    initiationId: query.initiationId || 0,
    scenarioType: 'BASE',
    contractAmount: 0,
    assumptions: '',
    targetMargin: 0.2,
    remark: '',
  })
  dialogVisible.value = true
}

/** 提交新建表单：校验通过后调用创建接口，引擎自动测算毛利/毛利率 */
async function submit() {
  if (!formRef.value) return
  try {
    submitting.value = true
    await formRef.value.validate()
    await createProfitSimulation(form)
    ElMessage.success('创建成功，引擎已自动测算毛利/毛利率')
    dialogVisible.value = false
    fetchList()
    fetchCompare()
  } catch {
    // 校验或创建失败，保持弹窗打开
  } finally {
    submitting.value = false
  }
}

/** 状态流转：DRAFT → SUBMITTED，提交审批 */
async function onSubmit(row: ProfitSimulationVO) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(`确认提交测算 ${row.simulationName} 审批？`, '提示', { type: 'info' })
    await changeSimulationStatus({ id: row.id, targetStatus: 'SUBMITTED' })
    ElMessage.success('已提交审批')
    fetchList()
  } catch { /* 用户取消 */ }
}
/** 状态流转：SUBMITTED → APPROVED，记录审批意见与审批人 */
async function onApprove(row: ProfitSimulationVO) {
  if (!row.id) return
  try {
    const { value: comment } = await ElMessageBox.prompt('请输入审批意见', '审批通过', { confirmButtonText: '通过', cancelButtonText: '取消' })
    await changeSimulationStatus({
      id: row.id,
      targetStatus: 'APPROVED',
      approvalComment: comment,
      approverName: userStore.userInfo?.realName || 'system',
    })
    ElMessage.success('已批准')
    fetchList()
  } catch { /* 用户取消 */ }
}
/** 状态流转：SUBMITTED → REJECTED，记录驳回原因 */
async function onReject(row: ProfitSimulationVO) {
  if (!row.id) return
  try {
    const { value: comment } = await ElMessageBox.prompt('请输入驳回原因', '驳回测算', { confirmButtonText: '驳回', cancelButtonText: '取消' })
    await changeSimulationStatus({
      id: row.id,
      targetStatus: 'REJECTED',
      approvalComment: comment,
      approverName: userStore.userInfo?.realName || 'system',
    })
    ElMessage.success('已驳回')
    fetchList()
  } catch { /* 用户取消 */ }
}
/** 删除测算版本（二次确认） */
async function onDelete(row: ProfitSimulationVO) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(`确认删除测算 ${row.simulationName}？`, '提示', { type: 'warning' })
    await deleteProfitSimulation(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 用户取消 */ }
}

// 简易对比柱状图
const compareBars = computed(() => {
  return compareData.value.map((c: any) => ({
    name: c.simulationName || c.simulationCode,
    type: c.scenarioType,
    margin: Number(c.grossMargin ?? 0) * 100,
    revenue: Number(c.externalRevenue ?? 0),
    profit: Number(c.grossProfit ?? 0),
  }))
})

onMounted(() => {
  if (query.initiationId) {
    fetchList()
    fetchCompare()
  }
})
</script>

<template>
  <div class="sim-page">
    <!-- 多版本对比 -->
    <el-card v-if="query.initiationId && compareBars.length" shadow="never" class="compare-card">
      <template #header>
        <div class="card-header">
          <span><el-icon><Histogram /></el-icon> 多版本对比</span>
          <el-tag type="info">项目 #{{ query.initiationId }} · {{ compareBars.length }} 个版本</el-tag>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col v-for="(c, i) in compareBars" :key="i" :span="24 / Math.min(compareBars.length, 4)">
          <div class="cmp-item">
            <div class="cmp-name">
              <el-tag :color="scenarioMap[c.type]?.color" effect="dark" size="small">
                {{ scenarioMap[c.type]?.label || c.type }}
              </el-tag>
              <span>{{ c.name }}</span>
            </div>
            <div class="cmp-margin" :class="c.margin >= 20 ? 'success' : c.margin >= 10 ? 'warning' : 'danger'">
              毛利率 {{ c.margin.toFixed(1) }}%
            </div>
            <div class="cmp-revenue">收入 {{ fmtMoney(c.revenue) }}</div>
            <div class="cmp-profit">毛利 {{ fmtMoney(c.profit) }}</div>
          </div>
        </el-col>
      </el-row>
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
      <!-- 查询条件区：项目 ID / 场景 / 状态 -->
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
        <el-form-item label="场景">
          <el-select v-model="query.scenarioType" placeholder="全部" clearable style="width: 130px">
            <el-option label="基准" value="BASE" />
            <el-option label="乐观" value="OPTIMISTIC" />
            <el-option label="悲观" value="PESSIMISTIC" />
            <el-option label="自定义" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="待审批" value="SUBMITTED" />
            <el-option label="已批准" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
        </el-form-item>
      </template>
      <!-- 工具栏：新建测算按钮（受权限控制，未选项目时禁用） -->
      <template #toolbar>
        <el-button
          v-if="hasPerm(PC.EXECUTION_SIMULATION_CREATE)"
          type="primary"
          :icon="'Plus'"
          :disabled="!query.initiationId"
          @click="openCreate"
        >
          新建测算
        </el-button>
      </template>
      <!-- 数据表格：测算版本明细 + 状态流转操作列 -->
      <template #table="scope">
        <vxe-table :data="list" :loading="loading" border height="auto" :scroll-y="scope.tableProps.scrollY">
          <vxe-column field="simulationCode" title="编号" width="160" />
          <vxe-column field="simulationName" title="测算名称" min-width="160" show-overflow />
          <vxe-column field="version" title="版本" width="70" />
          <vxe-column field="scenarioType" title="场景" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.scenarioType" :color="scenarioMap[row.scenarioType]?.color" effect="dark" size="small">
                {{ scenarioMap[row.scenarioType]?.label || row.scenarioType }}
              </el-tag>
            </template>
          </vxe-column>
          <vxe-column field="contractAmount" title="合同金额" width="130">
            <template #default="{ row }"><span class="num">{{ fmtMoney(row.contractAmount) }}</span></template>
          </vxe-column>
          <vxe-column field="externalRevenue" title="测算收入" width="130">
            <template #default="{ row }"><span class="num">{{ fmtMoney(row.externalRevenue) }}</span></template>
          </vxe-column>
          <vxe-column field="internalCost" title="对内成本" width="130">
            <template #default="{ row }"><span class="num">{{ fmtMoney(row.internalCost) }}</span></template>
          </vxe-column>
          <vxe-column field="grossProfit" title="测算毛利" width="130">
            <template #default="{ row }">
              <span class="num" :class="(row.grossProfit ?? 0) >= 0 ? 'success' : 'danger'">
                {{ fmtMoney(row.grossProfit) }}
              </span>
            </template>
          </vxe-column>
          <vxe-column field="grossMargin" title="毛利率" width="100">
            <template #default="{ row }">
              <span :class="['num', (row.grossMargin ?? 0) >= 0.2 ? 'success' : (row.grossMargin ?? 0) >= 0.1 ? 'warning' : 'danger']">
                {{ fmtPct(row.grossMargin) }}
              </span>
            </template>
          </vxe-column>
          <vxe-column field="targetMargin" title="目标毛利率" width="110">
            <template #default="{ row }"><span class="num">{{ fmtPct(row.targetMargin) }}</span></template>
          </vxe-column>
          <vxe-column field="blendedRate" title="混合费率" width="100">
            <template #default="{ row }">
              <span class="num">{{ row.blendedRate ? `¥${Number(row.blendedRate).toFixed(0)}` : '-' }}</span>
            </template>
          </vxe-column>
          <vxe-column field="status" title="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.status" :type="statusMap[row.status]?.type || 'info'" size="small">
                {{ statusMap[row.status]?.label || row.status }}
              </el-tag>
            </template>
          </vxe-column>
          <vxe-column field="applicantName" title="申请人" width="100" />
          <vxe-column title="操作" width="240" fixed="right">
            <!-- 操作按钮按状态与权限动态显隐：提交/批准/驳回/删除 -->
            <template #default="{ row }">
              <el-button
                v-if="hasPerm(PC.EXECUTION_SIMULATION_CREATE) && row.status === 'DRAFT'"
                link
                type="primary"
                @click="onSubmit(row)"
              >提交</el-button>
              <el-button
                v-if="hasPerm(PC.EXECUTION_SIMULATION_APPROVE) && row.status === 'SUBMITTED'"
                link
                type="success"
                @click="onApprove(row)"
              >批准</el-button>
              <el-button
                v-if="hasPerm(PC.EXECUTION_SIMULATION_APPROVE) && row.status === 'SUBMITTED'"
                link
                type="danger"
                @click="onReject(row)"
              >驳回</el-button>
              <el-button
                v-if="hasPerm(PC.EXECUTION_SIMULATION_CREATE)"
                link
                type="danger"
                @click="onDelete(row)"
              >删除</el-button>
            </template>
          </vxe-column>
          <template #empty>
            <el-empty :description="query.initiationId ? '该项目暂无测算版本' : '请先选择项目 ID 后查询'" />
          </template>
        </vxe-table>
      </template>
    </PageLayout>

    <!-- 新建测算弹窗：表单字段 + 引擎自动测算说明 -->
    <el-dialog
      v-model="dialogVisible"
      title="新建利润测算"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="测算编号" prop="simulationCode">
          <el-input v-model="form.simulationCode" />
        </el-form-item>
        <el-form-item label="测算名称" prop="simulationName">
          <el-input v-model="form.simulationName" placeholder="例如：V1 基准测算" />
        </el-form-item>
        <el-form-item label="项目 ID" prop="initiationId">
          <el-input-number v-model="form.initiationId" :min="1" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item label="场景">
          <el-select v-model="form.scenarioType" style="width: 200px">
            <el-option label="基准 BASE" value="BASE" />
            <el-option label="乐观 OPTIMISTIC" value="OPTIMISTIC" />
            <el-option label="悲观 PESSIMISTIC" value="PESSIMISTIC" />
            <el-option label="自定义 CUSTOM" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="合同金额" prop="contractAmount">
          <el-input-number v-model="form.contractAmount" :min="0" :precision="2" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item label="目标毛利率" prop="targetMargin">
          <el-input-number v-model="form.targetMargin" :min="0" :max="1" :step="0.01" :precision="2" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item label="假设条件">
          <el-input v-model="form.assumptions" type="textarea" :rows="3" placeholder="JSON 文本或说明，例如：{&quot;hours&quot;: 1000, &quot;blendedRate&quot;: 1500}" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="系统将根据 assumptions 与双费率引擎自动测算：对外收入 / 对内成本 / 毛利 / 毛利率 / 混合费率"
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
.sim-page {
  .compare-card {
    margin-bottom: 16px;
    .card-header {
      display: flex; justify-content: space-between; align-items: center; font-weight: 600;
    }
    .cmp-item {
      text-align: center;
      padding: 12px;
      border-right: 1px solid #ebeef5;
      .cmp-name {
        display: flex; align-items: center; justify-content: center; gap: 4px;
        font-size: 13px; margin-bottom: 8px;
      }
      .cmp-margin {
        font-size: 20px; font-weight: 600; margin: 4px 0;
        &.success { color: #67c23a; }
        &.warning { color: #e6a23c; }
        &.danger { color: #f56c6c; }
      }
      .cmp-revenue, .cmp-profit { font-size: 12px; color: #606266; margin-top: 2px; }
    }
  }
  .num { font-variant-numeric: tabular-nums; }
  .success { color: #67c23a; }
  .warning { color: #e6a23c; }
  .danger { color: #f56c6c; }
}
</style>
