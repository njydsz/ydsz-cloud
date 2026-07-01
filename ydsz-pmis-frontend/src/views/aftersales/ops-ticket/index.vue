<script setup lang="ts">
/**
 * 运维工单管理 (P7)
 *
 * 优先级: P1/P2/P3/P4 (SLA 响应/解决时限不同)
 * 状态: OPEN/ASSIGNED/IN_PROGRESS/RESOLVED/CLOSED/CANCELLED
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pageOpsTickets,
  createOpsTicket,
  assignOpsTicket,
  changeOpsTicketStatus,
  closeAndEvaluateOpsTicket,
  scanOpsTicketSlaBreaches,
  slaSummaryOpsTicket,
  aggregateOpsTicketByStatus,
} from '@/api/execution/aftersales/ops-ticket'
import type {
  OpsTicketVO,
  OpsTicketCreateDTO,
  OpsTicketAssignDTO,
  OpsTicketStatusDTO,
} from '@/api/execution/aftersales/types'
import { PC } from '@/constants/permissionCodes'

const loading = ref(false)
const list = ref<OpsTicketVO[]>([])
const total = ref(0)
const slaList = ref<Array<Record<string, unknown>>>([])
const statusAgg = ref<Array<Record<string, unknown>>>([])
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  priority: '',
  initiationId: undefined as number | undefined,
  assigneeId: undefined as number | undefined,
})

const statusMap = {
  OPEN: { label: '待派单', type: 'warning' as const },
  ASSIGNED: { label: '已派单', type: 'primary' as const },
  IN_PROGRESS: { label: '处理中', type: 'primary' as const },
  RESOLVED: { label: '已解决', type: 'success' as const },
  CLOSED: { label: '已关闭', type: 'info' as const },
  CANCELLED: { label: '已取消', type: 'danger' as const },
}

const priorityMap = {
  P1: { label: 'P1 紧急', type: 'danger' as const },
  P2: { label: 'P2 高', type: 'warning' as const },
  P3: { label: 'P3 中', type: 'primary' as const },
  P4: { label: 'P4 低', type: 'info' as const },
}

const categoryMap: Record<string, string> = {
  BUG: '缺陷',
  DATA: '数据',
  CONFIG: '配置',
  PROCESS: '流程',
  OTHER: '其他',
}

async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageOpsTickets({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      priority: query.priority || undefined,
      initiationId: query.initiationId,
      assigneeId: query.assigneeId,
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function fetchStats() {
  try {
    slaList.value = await slaSummaryOpsTicket().then((r) => r.data as Array<Record<string, unknown>>)
  } catch {
    slaList.value = []
  }
  try {
    statusAgg.value = await aggregateOpsTicketByStatus().then((r) => r.data as Array<Record<string, unknown>>)
  } catch {
    statusAgg.value = []
  }
}

function handleReset() {
  query.keyword = ''
  query.status = ''
  query.priority = ''
  query.initiationId = undefined
  query.assigneeId = undefined
  query.page = 1
  fetchList()
}

const dialogVisible = ref(false)
const formRef = ref<any>()
const form = reactive<Partial<OpsTicketCreateDTO>>({
  initiationId: 0,
  priority: 'P3',
  title: '',
  category: 'OTHER',
})

const formRules = {
  initiationId: [{ required: true, message: '项目 ID 必填', trigger: 'blur' }],
  title: [{ required: true, message: '工单标题必填', trigger: 'blur' }],
  priority: [{ required: true, message: '优先级必填', trigger: 'change' }],
}

function openCreate() {
  Object.assign(form, {
    initiationId: 0,
    priority: 'P3',
    title: '',
    description: '',
    category: 'OTHER',
    warrantyId: undefined,
    reporterId: undefined,
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  await createOpsTicket(form as OpsTicketCreateDTO)
  ElMessage.success('已创建')
  dialogVisible.value = false
  fetchList()
}

const assignVisible = ref(false)
const assignForm = reactive<{ id?: number; assigneeId: number; comment: string }>({
  id: undefined,
  assigneeId: 0,
  comment: '',
})

function openAssign(row: OpsTicketVO) {
  assignForm.id = row.id
  assignForm.assigneeId = row.assigneeId || 0
  assignForm.comment = ''
  assignVisible.value = true
}

async function submitAssign() {
  if (!assignForm.id || !assignForm.assigneeId) {
    ElMessage.warning('请填写处理人 ID')
    return
  }
  const dto: OpsTicketAssignDTO = { id: assignForm.id, assigneeId: assignForm.assigneeId, comment: assignForm.comment }
  await assignOpsTicket(dto)
  ElMessage.success('已派单')
  assignVisible.value = false
  fetchList()
}

async function handleStatus(row: OpsTicketVO, target: string) {
  const targetText = (statusMap as any)[target]?.label || target
  try {
    let dto: OpsTicketStatusDTO = { id: row.id, targetStatus: target }
    if (target === 'RESOLVED') {
      const { value } = await ElMessageBox.prompt('请输入解决说明', '工单已解决', {
        inputValidator: (v) => !!v || '说明必填',
      })
      dto = { ...dto, resolutionNote: value }
      // 通过 changeStatus 提交
      await changeOpsTicketStatus({
        id: row.id,
        targetStatus: target,
        resolutionNote: value,
      } as any)
    } else if (target === 'CANCELLED') {
      const { value } = await ElMessageBox.prompt('请输入取消原因', '取消工单', {
        inputValidator: (v) => !!v || '原因必填',
      })
      await changeOpsTicketStatus({ ...dto, comment: value })
    } else {
      await changeOpsTicketStatus(dto)
    }
    ElMessage.success(`已变更为「${targetText}」`)
    fetchList()
  } catch { /* 取消 */ }
}

const evalVisible = ref(false)
const evalForm = reactive<{ id?: number; score: number; comment: string }>({
  id: undefined,
  score: 5,
  comment: '',
})

function openEvaluate(row: OpsTicketVO) {
  evalForm.id = row.id
  evalForm.score = 5
  evalForm.comment = ''
  evalVisible.value = true
}

async function submitEvaluate() {
  if (!evalForm.id) return
  await closeAndEvaluateOpsTicket({
    id: evalForm.id,
    targetStatus: 'CLOSED',
    customerScore: evalForm.score,
    customerComment: evalForm.comment,
  } as any)
  ElMessage.success('已关闭并评价')
  evalVisible.value = false
  fetchList()
}

async function handleScan() {
  const n = await scanOpsTicketSlaBreaches()
  ElMessage.success(`扫描到 ${n} 条 SLA 超时`)
  fetchList()
  fetchStats()
}

function slaRateText(row: Record<string, unknown>, key: string) {
  const v = Number(row[key] || 0)
  if (!isFinite(v)) return '0%'
  return (v * 100).toFixed(1) + '%'
}

onMounted(() => {
  fetchList()
  fetchStats()
})
</script>

<template>
  <PageLayout
    v-model:query="query"
    :list="list"
    :total="total"
    :loading="loading"
    @query="query.page = 1; fetchList()"
    @reset="handleReset"
    @page-change="fetchList"
    @refresh="() => { fetchList(); fetchStats(); }"
  >
    <template #search>
      <el-form-item label="关键字"><el-input v-model="query.keyword" placeholder="编号/标题" clearable /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="优先级">
        <el-select v-model="query.priority" placeholder="全部" clearable style="width: 110px">
          <el-option v-for="(v, k) in priorityMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目 ID"><el-input-number v-model="query.initiationId" :min="0" :controls="false" /></el-form-item>
      <el-form-item label="处理人 ID"><el-input-number v-model="query.assigneeId" :min="0" :controls="false" /></el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.AFTERSALES_OPS_TICKET_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        新增工单
      </el-button>
      <el-button v-permission="[PC.AFTERSALES_OPS_TICKET_SCAN]" type="warning" :icon="'Bell'" @click="handleScan">
        SLA 扫描
      </el-button>
    </template>

    <!-- SLA 达成率 -->
    <el-row v-if="slaList.length" :gutter="12" class="mb-3">
      <el-col v-for="row in slaList" :key="String(row.priority)" :span="6">
        <el-card shadow="hover">
          <div class="text-sm text-gray-500">
            <StatusTag :value="String(row.priority)" :map="priorityMap" />
            <span class="ml-2">共 {{ row.totalCount }} 条</span>
          </div>
          <div class="text-xs mt-2">
            响应 SLA 达成：<b :class="Number(row.responseSlaRate) < 0.8 ? 'text-red-500' : 'text-green-600'">{{ slaRateText(row, 'responseSlaRate') }}</b>
          </div>
          <div class="text-xs">
            解决 SLA 达成：<b :class="Number(row.resolveSlaRate) < 0.8 ? 'text-red-500' : 'text-green-600'">{{ slaRateText(row, 'resolveSlaRate') }}</b>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <template #table>
      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="ticketCode" title="工单编号" width="180" />
        <vxe-column field="title" title="标题" min-width="220" show-overflow />
        <vxe-column field="initiationName" title="项目" min-width="160" show-overflow />
        <vxe-column field="category" title="类型" width="90">
          <template #default="{ row }">{{ categoryMap[row.category as string] || row.category || '-' }}</template>
        </vxe-column>
        <vxe-column field="priority" title="优先级" width="100">
          <template #default="{ row }"><StatusTag :value="row.priority" :map="priorityMap" /></template>
        </vxe-column>
        <vxe-column field="reporterName" title="报修人" width="100" />
        <vxe-column field="assigneeName" title="处理人" width="100" />
        <vxe-column field="responseDueAt" title="响应截止" width="170" />
        <vxe-column field="resolveDueAt" title="解决截止" width="170" />
        <vxe-column label="SLA" width="160">
          <template #default="{ row }">
            <el-tag v-if="row.responseSlaBreached" type="danger" size="small">响应超时</el-tag>
            <el-tag v-if="row.resolveSlaBreached" type="danger" size="small" class="ml-1">解决超时</el-tag>
            <span v-if="!row.responseSlaBreached && !row.resolveSlaBreached" class="text-gray-400">正常</span>
          </template>
        </vxe-column>
        <vxe-column field="status" title="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column field="customerScore" title="评分" width="70" align="center" />
        <vxe-column title="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'OPEN'"
              v-permission="[PC.AFTERSALES_OPS_TICKET_ASSIGN]"
              link
              type="primary"
              size="small"
              @click="openAssign(row)"
            >派单</el-button>
            <el-button
              v-if="row.status === 'ASSIGNED'"
              v-permission="[PC.AFTERSALES_OPS_TICKET_STATUS]"
              link
              type="primary"
              size="small"
              @click="handleStatus(row, 'IN_PROGRESS')"
            >开始处理</el-button>
            <el-button
              v-if="row.status === 'IN_PROGRESS'"
              v-permission="[PC.AFTERSALES_OPS_TICKET_STATUS]"
              link
              type="success"
              size="small"
              @click="handleStatus(row, 'RESOLVED')"
            >标记解决</el-button>
            <el-button
              v-if="row.status === 'RESOLVED'"
              v-permission="[PC.AFTERSALES_OPS_TICKET_EVALUATE]"
              link
              type="success"
              size="small"
              @click="openEvaluate(row)"
            >关闭评价</el-button>
            <el-button
              v-if="['OPEN', 'ASSIGNED', 'IN_PROGRESS'].includes(row.status || '')"
              v-permission="[PC.AFTERSALES_OPS_TICKET_STATUS]"
              link
              type="danger"
              size="small"
              @click="handleStatus(row, 'CANCELLED')"
            >取消</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <el-dialog v-model="dialogVisible" title="新增工单" width="560px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="项目 ID" prop="initiationId">
          <el-input-number v-model="form.initiationId" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="工单标题" prop="title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="(v, k) in categoryMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-select v-model="form.priority" style="width: 100%">
            <el-option v-for="(v, k) in priorityMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="报修人 ID">
          <el-input-number v-model="form.reporterId" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="关联质保期">
          <el-input-number v-model="form.warrantyId" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignVisible" title="派单" width="460px">
      <el-form label-width="100px">
        <el-form-item label="工单 ID"><el-input :model-value="assignForm.id" disabled /></el-form-item>
        <el-form-item label="处理人 ID" required>
          <el-input-number v-model="assignForm.assigneeId" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="assignForm.comment" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAssign">派单</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="evalVisible" title="关闭工单并评价" width="460px">
      <el-form label-width="100px">
        <el-form-item label="工单 ID"><el-input :model-value="evalForm.id" disabled /></el-form-item>
        <el-form-item label="评分 (1-5)" required>
          <el-rate v-model="evalForm.score" :max="5" />
        </el-form-item>
        <el-form-item label="评价说明">
          <el-input v-model="evalForm.comment" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="evalVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEvaluate">提交并关闭</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
