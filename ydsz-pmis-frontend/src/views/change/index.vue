<script setup lang="ts">
/**
 * 项目变更管理（批次 19 补全 + 批次 21 / P2 useTable 重构）
 *
 * 状态机: DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED
 *         APPROVED → EXECUTING → EXECUTED
 *         DRAFT/SUBMITTED/UNDER_REVIEW/APPROVED/EXECUTING → CANCELLED
 * 终态:   EXECUTED / REJECTED / CANCELLED
 * 影响等级: LOW / MEDIUM / HIGH（后端 ChangeImpactEvaluator 多因子评估）
 * 重大变更: GM + CFO 双审批
 */
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pageProjectChanges,
  getProjectChange,
  createProjectChange,
  changeProjectChangeStatus,
  deleteProjectChange,
  getAllowedTransitions,
} from '@/api/project/change'
import type {
  ProjectChangeVO,
  ProjectChangeCreateDTO,
} from '@/api/project/change/types'
import { PC } from '@/constants/permissionCodes'
import { useTable } from '@/composables/useTable'

// ===== 列表查询 (useTable composable) =====
const {
  loading,
  list,
  total,
  query,
  fetchData: fetchList,
  handleQuery,
  resetQuery,
  handlePageChange,
} = useTable<{
  page: number
  size: number
  keyword: string
  status: string
  changeType: string
  initiationId: number | undefined
}>(async (q) => {
  const { data } = await pageProjectChanges(q.page, q.size, {
    keyword: q.keyword || undefined,
    changeType: q.changeType || undefined,
    status: q.status || undefined,
    initiationId: q.initiationId,
  })
  return { data: { list: data.list || [], total: data.total || 0 } }
}, { defaultSize: 10 })

// 状态映射（与后端 ChangeStatus 枚举对齐）
const statusMap: Record<string, { label: string; type: 'info' | 'warning' | 'success' | 'danger' | 'primary' }> = {
  DRAFT:        { label: '草稿',     type: 'info' },
  SUBMITTED:    { label: '已提交',   type: 'warning' },
  UNDER_REVIEW: { label: '评审中',   type: 'warning' },
  APPROVED:     { label: '已批准',   type: 'success' },
  REJECTED:     { label: '已驳回',   type: 'danger' },
  EXECUTING:    { label: '执行中',   type: 'primary' },
  EXECUTED:     { label: '已执行',   type: 'success' },
  CANCELLED:    { label: '已取消',   type: 'info' },
}

// 变更类型（与后端 ChangeType 枚举对齐：SCOPE/COST/CONTRACT/STAFF/SCHEDULE）
const typeMap: Record<string, { label: string; color: string }> = {
  SCOPE:    { label: '范围变更',   color: '#409EFF' },
  COST:     { label: '成本变更',   color: '#F56C6C' },
  CONTRACT: { label: '合同变更',   color: '#E6A23C' },
  STAFF:    { label: '人员变更',   color: '#67C23A' },
  SCHEDULE: { label: '进度变更',   color: '#909399' },
}

// 风险等级（与后端 RiskLevel 枚举对齐）
const riskMap: Record<string, { label: string; color: string }> = {
  LOW:    { label: '低风险', color: '#67C23A' },
  MEDIUM: { label: '中风险', color: '#E6A23C' },
  HIGH:   { label: '高风险', color: '#F56C6C' },
}

// 状态机迁移规则 (前端兜底; 服务端 allowed-transitions 优先)
const transitions: Record<string, string[]> = {
  DRAFT:        ['SUBMITTED', 'CANCELLED'],
  SUBMITTED:    ['UNDER_REVIEW', 'CANCELLED'],
  UNDER_REVIEW: ['APPROVED', 'REJECTED'],
  APPROVED:     ['EXECUTING', 'CANCELLED'],
  EXECUTING:    ['EXECUTED', 'CANCELLED'],
  EXECUTED:     [],
  REJECTED:     [],
  CANCELLED:    [],
}

// 优先从后端拉取 allowed-transitions; 失败时用前端兜底
const backendAllowedMap = reactive<Record<number, string[]>>({})

async function loadAllowedTransitions(id: number) {
  if (backendAllowedMap[id]) return
  try {
    const { data } = await getAllowedTransitions(id)
    backendAllowedMap[id] = data || []
  } catch {
    backendAllowedMap[id] = []
  }
}

function allowedTargets(row: ProjectChangeVO): string[] {
  return backendAllowedMap[row.id]?.length
    ? backendAllowedMap[row.id]
    : transitions[row.status] || []
}

function handleReset() {
  resetQuery()
}

// ========== 新增/编辑 ==========
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<any>()
const form = reactive<Partial<ProjectChangeCreateDTO>>({
  changeCode: '',
  initiationId: undefined,
  changeType: 'SCOPE',
  changeTitle: '',
  changeReason: '',
  changeDesc: '',
  budgetImpact: undefined,
  contractImpact: undefined,
  scheduleImpactDays: undefined,
  profitImpact: undefined,
  affectedWbsCount: undefined,
  affectedStaffCount: undefined,
  contractId: undefined,
  applicantId: 1,
  applicantName: '',
  remark: '',
})

const formRules = {
  changeCode: [{ required: true, message: '变更编号必填', trigger: 'blur' }],
  initiationId: [{ required: true, message: '项目 ID 必填', trigger: 'blur' }],
  changeTitle: [{ required: true, message: '变更标题必填', trigger: 'blur' }],
}

function openCreate() {
  Object.assign(form, {
    changeCode: `CHG-${Date.now().toString().slice(-8)}`,
    initiationId: undefined,
    changeType: 'SCOPE',
    changeTitle: '',
    changeReason: '',
    changeDesc: '',
    budgetImpact: undefined,
    contractImpact: undefined,
    scheduleImpactDays: undefined,
    profitImpact: undefined,
    affectedWbsCount: undefined,
    affectedStaffCount: undefined,
    contractId: undefined,
    applicantId: 1,
    applicantName: '',
    remark: '',
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    await createProjectChange(form as ProjectChangeCreateDTO)
    ElMessage.success('变更已提交, 后端已自动评估影响等级')
    dialogVisible.value = false
    fetchList()
  } finally {
    submitting.value = false
  }
}

// ========== 状态迁移 ==========
async function handleStatus(row: ProjectChangeVO, target: string) {
  const targetLabel = statusMap[target]?.label || target
  let extraHint = ''
  if (row.majorFlag === 1 && (target === 'APPROVED' || target === 'UNDER_REVIEW')) {
    extraHint = '\n(此为重大变更, ' + (row.approverRoles || 'GM/CFO') + ' 需双审批)'
  }
  try {
    await ElMessageBox.confirm(
      `确认将变更「${row.changeCode}」状态变更为「${targetLabel}」?${extraHint}`,
      '状态迁移',
      { type: 'warning' },
    )
    await changeProjectChangeStatus({ id: row.id, targetStatus: target })
    ElMessage.success('状态已更新')
    fetchList()
  } catch { /* 取消 */ }
}

// ========== 删除 ==========
async function handleDelete(row: ProjectChangeVO) {
  try {
    await ElMessageBox.confirm(
      `确认删除变更「${row.changeCode}」?仅 DRAFT/REJECTED/CANCELLED 状态可删除。`,
      '删除确认',
      { type: 'warning' },
    )
    await deleteProjectChange(row.id)
    ElMessage.success('已删除')
    fetchList()
  } catch { /* 取消 */ }
}

// ========== 详情抽屉 ==========
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ProjectChangeVO | null>(null)

async function openDetail(row: ProjectChangeVO) {
  detail.value = row
  detailVisible.value = true
  detailLoading.value = true
  // 并行加载 allowed-transitions
  loadAllowedTransitions(row.id)
  try {
    const { data } = await getProjectChange(row.id)
    detail.value = data
  } finally {
    detailLoading.value = false
  }
}

function fmtAmount(n?: number) {
  if (n === undefined || n === null) return '-'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function fmtImpact(n?: number) {
  if (n === undefined || n === null) return '-'
  const prefix = n > 0 ? '+' : ''
  return prefix + fmtAmount(n)
}

function fmtPct(n?: number) {
  if (n === undefined || n === null) return '-'
  return (n * 100).toFixed(2) + '%'
}

// 影响等级预估（前端即时提示, 后端评估为准）
const estimatedRisk = computed(() => {
  let score = 0
  if (form.budgetImpact && Math.abs(form.budgetImpact) > 100000) score += 2
  else if (form.budgetImpact && Math.abs(form.budgetImpact) > 10000) score += 1
  if (form.scheduleImpactDays && Math.abs(form.scheduleImpactDays) > 14) score += 2
  else if (form.scheduleImpactDays && Math.abs(form.scheduleImpactDays) > 3) score += 1
  if (form.affectedWbsCount && form.affectedWbsCount > 5) score += 1
  if (form.affectedStaffCount && form.affectedStaffCount > 3) score += 1
  if (score >= 4) return { level: 'HIGH', label: '高风险(需 GM+CFO 双审批)', color: '#F56C6C' }
  if (score >= 2) return { level: 'MEDIUM', label: '中风险', color: '#E6A23C' }
  return { level: 'LOW', label: '低风险', color: '#67C23A' }
})

onMounted(() => {
  fetchList()
})
</script>

<template>
  <PageLayout
    v-model:query="query"
    :list="list"
    :total="total"
    :loading="loading"
    @query="handleQuery"
    @reset="handleReset"
    @page-change="handlePageChange"
    @refresh="fetchList"
  >
    <template #search>
      <el-form-item label="关键字">
        <el-input v-model="query.keyword" placeholder="编号/标题/原因" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.changeType" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目 ID">
        <el-input v-model.number="query.initiationId" placeholder="立项 ID" clearable style="width: 140px" />
      </el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.PROJECT_CHANGE_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        新增变更
      </el-button>
    </template>

    <template #table>
      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="changeCode" title="变更编号" width="170" fixed="left" />
        <vxe-column field="changeTitle" title="变更标题" min-width="200" show-overflow />
        <vxe-column field="changeType" title="类型" width="110">
          <template #default="{ row }">
            <el-tag v-if="typeMap[row.changeType]" :color="typeMap[row.changeType].color" effect="light" size="small" style="color: #fff; border: none">
              {{ typeMap[row.changeType].label }}
            </el-tag>
            <span v-else>{{ row.changeType || '-' }}</span>
          </template>
        </vxe-column>
        <vxe-column field="status" title="状态" width="100">
          <template #default="{ row }">
            <StatusTag :value="row.status" :map="statusMap" />
          </template>
        </vxe-column>
        <vxe-column field="riskLevelAfter" title="影响等级" width="100">
          <template #default="{ row }">
            <el-tag v-if="riskMap[row.riskLevelAfter]" :type="row.riskLevelAfter === 'HIGH' ? 'danger' : row.riskLevelAfter === 'MEDIUM' ? 'warning' : 'success'" size="small">
              {{ riskMap[row.riskLevelAfter].label }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </vxe-column>
        <vxe-column title="重大" width="60" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.majorFlag === 1" type="danger" size="small">重大</el-tag>
            <span v-else>-</span>
          </template>
        </vxe-column>
        <vxe-column field="budgetImpact" title="预算影响" width="120" align="right">
          <template #default="{ row }">
            <span :style="{ color: (row.budgetImpact || 0) > 0 ? '#F56C6C' : (row.budgetImpact || 0) < 0 ? '#67C23A' : undefined }">
              {{ fmtImpact(row.budgetImpact) }}
            </span>
          </template>
        </vxe-column>
        <vxe-column field="scheduleImpactDays" title="进度影响(天)" width="110" align="right">
          <template #default="{ row }">
            <span :style="{ color: (row.scheduleImpactDays || 0) > 0 ? '#E6A23C' : (row.scheduleImpactDays || 0) < 0 ? '#67C23A' : undefined }">
              {{ fmtImpact(row.scheduleImpactDays) }}
            </span>
          </template>
        </vxe-column>
        <vxe-column field="profitImpactPct" title="利润影响%" width="100" align="right">
          <template #default="{ row }">
            <span :style="{ color: (row.profitImpactPct || 0) < 0 ? '#F56C6C' : '#67C23A' }">
              {{ fmtPct(row.profitImpactPct) }}
            </span>
          </template>
        </vxe-column>
        <vxe-column field="applicantName" title="申请人" width="100" />
        <vxe-column field="createdAt" title="创建时间" width="170" />
        <vxe-column title="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button
              v-for="target in allowedTargets(row)"
              :key="target"
              link
              size="small"
              :type="target === 'APPROVED' ? 'success' : target === 'REJECTED' ? 'danger' : 'primary'"
              :disabled="row.majorFlag === 1 && target === 'APPROVED' && row.status === 'UNDER_REVIEW'"
              @click="handleStatus(row, target)"
            >
              {{ statusMap[target]?.label || target }}
            </el-button>
            <el-button
              v-if="['DRAFT', 'REJECTED', 'CANCELLED'].includes(row.status)"
              v-permission="[PC.PROJECT_CHANGE_STATUS]"
              link
              type="danger"
              size="small"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <!-- 新增变更弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增项目变更" width="900px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="变更编号" prop="changeCode">
              <el-input v-model="form.changeCode" placeholder="如 CHG-2026-001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目 ID" prop="initiationId">
              <el-input v-model.number="form.initiationId" placeholder="立项 ID" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="变更类型">
              <el-select v-model="form.changeType" style="width: 100%">
                <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预估影响等级">
              <el-tag :color="estimatedRisk.color" effect="dark" size="default" style="color: #fff; border: none">
                {{ estimatedRisk.label }}
              </el-tag>
              <span style="margin-left: 8px; color: #909399; font-size: 12px">
                (后端评估为准)
              </span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="变更标题" prop="changeTitle">
          <el-input v-model="form.changeTitle" placeholder="简要描述" />
        </el-form-item>
        <el-form-item label="变更原因">
          <el-input v-model="form.changeReason" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="详细说明">
          <el-input v-model="form.changeDesc" type="textarea" :rows="3" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="预算影响">
              <el-input v-model.number="form.budgetImpact" type="number" placeholder="元" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="合同影响">
              <el-input v-model.number="form.contractImpact" type="number" placeholder="元" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="进度影响(天)">
              <el-input v-model.number="form.scheduleImpactDays" type="number" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="利润影响">
              <el-input v-model.number="form.profitImpact" type="number" placeholder="元" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="影响 WBS 数">
              <el-input v-model.number="form.affectedWbsCount" type="number" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="影响人员数">
              <el-input v-model.number="form.affectedStaffCount" type="number" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="关联合同 ID">
              <el-input v-model.number="form.contractId" type="number" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">提交</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="变更详情" size="60%">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="变更编号">{{ detail.changeCode }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <StatusTag :value="detail.status" :map="statusMap" />
            </el-descriptions-item>
            <el-descriptions-item label="类型">
              <el-tag v-if="typeMap[detail.changeType]" :color="typeMap[detail.changeType].color" effect="light" size="small" style="color: #fff; border: none">
                {{ typeMap[detail.changeType].label }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="影响等级">
              <el-tag v-if="riskMap[detail.riskLevelAfter]" :type="detail.riskLevelAfter === 'HIGH' ? 'danger' : detail.riskLevelAfter === 'MEDIUM' ? 'warning' : 'success'" size="small">
                {{ riskMap[detail.riskLevelAfter].label }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="重大变更">
              <el-tag v-if="detail.majorFlag === 1" type="danger" size="small">是</el-tag>
              <span v-else>否</span>
            </el-descriptions-item>
            <el-descriptions-item label="项目 ID">{{ detail.initiationId }}</el-descriptions-item>
            <el-descriptions-item label="标题" :span="2">{{ detail.changeTitle }}</el-descriptions-item>
            <el-descriptions-item label="原因" :span="2">{{ detail.changeReason || '-' }}</el-descriptions-item>
            <el-descriptions-item label="详细说明" :span="2">{{ detail.changeDesc || '-' }}</el-descriptions-item>
            <el-descriptions-item label="预算影响">{{ fmtImpact(detail.budgetImpact) }}</el-descriptions-item>
            <el-descriptions-item label="合同影响">{{ fmtImpact(detail.contractImpact) }}</el-descriptions-item>
            <el-descriptions-item label="进度影响">{{ fmtImpact(detail.scheduleImpactDays) }} 天</el-descriptions-item>
            <el-descriptions-item label="利润影响">{{ fmtPct(detail.profitImpactPct) }}</el-descriptions-item>
            <el-descriptions-item label="申请人">{{ detail.applicantName }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.approverRoles" label="需审批角色" :span="2">
              <el-tag type="warning" size="small">{{ detail.approverRoles }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.remark" label="备注" :span="2">{{ detail.remark }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </div>
    </el-drawer>
  </PageLayout>
</template>
