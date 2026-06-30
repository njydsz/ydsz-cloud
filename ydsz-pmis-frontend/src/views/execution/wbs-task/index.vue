<script setup lang="ts">
/**
 * WBS 任务管理
 *
 * 状态: PLANNED -> IN_PROGRESS -> BLOCKED -> IN_REVIEW -> COMPLETED/CANCELLED
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pageWbsTasks,
  createWbsTask,
  changeWbsTaskStatus,
  deleteWbsTask,
} from '@/api/execution/wbs-task'
import type { WbsTaskVO, WbsTaskCreateDTO } from '@/api/execution/wbs-task/types'
import { PC } from '@/constants/permissionCodes'

const loading = ref(false)
const list = ref<WbsTaskVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  initiationId: undefined as number | undefined,
  ownerId: undefined as number | undefined,
})

const statusMap = {
  PLANNED: { label: '计划中', type: 'info' as const },
  IN_PROGRESS: { label: '进行中', type: 'primary' as const },
  BLOCKED: { label: '阻塞', type: 'danger' as const },
  IN_REVIEW: { label: '评审中', type: 'warning' as const },
  COMPLETED: { label: '已完成', type: 'success' as const },
  CANCELLED: { label: '已取消', type: 'info' as const },
}

const priorityMap = {
  LOW: { label: '低', type: 'info' as const },
  NORMAL: { label: '普通', type: 'primary' as const },
  HIGH: { label: '高', type: 'warning' as const },
  URGENT: { label: '紧急', type: 'danger' as const },
}

const typeMap = {
  TASK: { label: '任务' },
  MILESTONE: { label: '里程碑' },
  SUMMARY: { label: '汇总' },
}

async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageWbsTasks(query.page, query.size, {
      keyword: query.keyword,
      status: query.status,
      initiationId: query.initiationId,
      ownerId: query.ownerId,
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handleReset() {
  query.keyword = ''
  query.status = ''
  query.initiationId = undefined
  query.ownerId = undefined
  query.page = 1
  fetchList()
}

const dialogVisible = ref(false)
const formRef = ref<any>()
const form = reactive<Partial<WbsTaskCreateDTO>>({
  taskCode: '',
  taskName: '',
  initiationId: 0,
  taskType: 'TASK',
  priority: 'NORMAL',
  plannedStartDate: '',
  plannedEndDate: '',
  plannedEffort: undefined,
  ownerId: 0,
})

const formRules = {
  taskCode: [{ required: true, message: '任务编码必填', trigger: 'blur' }],
  taskName: [{ required: true, message: '任务名称必填', trigger: 'blur' }],
  initiationId: [{ required: true, message: '项目 ID 必填', trigger: 'blur' }],
  ownerId: [{ required: true, message: '负责人 ID 必填', trigger: 'blur' }],
}

function openCreate() {
  Object.assign(form, {
    taskCode: '',
    taskName: '',
    initiationId: 0,
    taskType: 'TASK',
    priority: 'NORMAL',
    plannedStartDate: '',
    plannedEndDate: '',
    plannedEffort: undefined,
    ownerId: 0,
    description: '',
    deliverable: '',
    dependsOn: '',
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  await createWbsTask(form as WbsTaskCreateDTO)
  ElMessage.success('创建成功')
  dialogVisible.value = false
  fetchList()
}

async function handleDelete(row: WbsTaskVO) {
  try {
    await ElMessageBox.confirm(`确认删除任务「${row.taskName}」吗？`, '提示', { type: 'warning' })
    await deleteWbsTask(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 取消 */ }
}

async function handleStatus(row: WbsTaskVO, target: string) {
  const targetText = (statusMap as any)[target]?.label || target
  try {
    await ElMessageBox.confirm(`确认将状态变更为「${targetText}」吗？`, '提示', { type: 'warning' })
    await changeWbsTaskStatus({ id: row.id, targetStatus: target })
    ElMessage.success('状态已更新')
    fetchList()
  } catch { /* 取消 */ }
}

async function handleProgress(row: WbsTaskVO) {
  try {
    const { value } = await ElMessageBox.prompt('请输入完成进度 (0-100)', '更新进度', {
      inputPattern: /^\d+(\.\d{1,2})?$/,
      inputErrorMessage: '请输入 0-100 之间的数字',
    })
    const pct = Number(value)
    if (pct < 0 || pct > 100) {
      ElMessage.warning('进度应在 0-100 之间')
      return
    }
    await changeWbsTaskStatus({ id: row.id, targetStatus: 'IN_PROGRESS', progressPct: pct })
    ElMessage.success('进度已更新')
    fetchList()
  } catch { /* 取消 */ }
}

onMounted(fetchList)
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
    @refresh="fetchList"
  >
    <template #search>
      <el-form-item label="关键字">
        <el-input v-model="query.keyword" placeholder="编码/名称" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目 ID">
        <el-input-number v-model="query.initiationId" :min="0" :controls="false" />
      </el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.EXECUTION_WBS_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        新增任务
      </el-button>
    </template>

    <template #table>
      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="taskCode" title="编码" width="140" />
        <vxe-column field="taskName" title="任务名称" min-width="200" show-overflow />
        <vxe-column field="initiationName" title="项目" width="160" show-overflow />
        <vxe-column field="taskType" title="类型" width="80" align="center">
          <template #default="{ row }">{{ typeMap[row.taskType as keyof typeof typeMap]?.label || row.taskType || '-' }}</template>
        </vxe-column>
        <vxe-column field="priority" title="优先级" width="80" align="center">
          <template #default="{ row }"><StatusTag :value="row.priority" :map="priorityMap" /></template>
        </vxe-column>
        <vxe-column field="ownerName" title="负责人" width="100" />
        <vxe-column field="plannedStartDate" title="计划开始" width="110" />
        <vxe-column field="plannedEndDate" title="计划结束" width="110" />
        <vxe-column field="plannedEffort" title="计划工时" width="100" align="right" />
        <vxe-column field="progressPct" title="进度" width="180">
          <template #default="{ row }">
            <el-progress :percentage="Number(row.progressPct || 0)" :stroke-width="10" />
          </template>
        </vxe-column>
        <vxe-column field="status" title="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column title="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PLANNED'" v-permission="[PC.EXECUTION_WBS_STATUS]" link type="primary" size="small" @click="handleStatus(row, 'IN_PROGRESS')">
              启动
            </el-button>
            <el-button v-if="row.status === 'IN_PROGRESS'" v-permission="[PC.EXECUTION_WBS_STATUS]" link type="primary" size="small" @click="handleProgress(row)">
              更新进度
            </el-button>
            <el-button v-if="row.status === 'IN_PROGRESS'" v-permission="[PC.EXECUTION_WBS_STATUS]" link type="warning" size="small" @click="handleStatus(row, 'IN_REVIEW')">
              提评审
            </el-button>
            <el-button v-if="row.status === 'IN_PROGRESS'" v-permission="[PC.EXECUTION_WBS_STATUS]" link type="danger" size="small" @click="handleStatus(row, 'BLOCKED')">
              阻塞
            </el-button>
            <el-button v-if="row.status === 'IN_REVIEW'" v-permission="[PC.EXECUTION_WBS_STATUS]" link type="success" size="small" @click="handleStatus(row, 'COMPLETED')">
              完成
            </el-button>
            <el-button v-if="row.status === 'BLOCKED'" v-permission="[PC.EXECUTION_WBS_STATUS]" link type="primary" size="small" @click="handleStatus(row, 'IN_PROGRESS')">
              解除阻塞
            </el-button>
            <el-button v-permission="[PC.EXECUTION_WBS_DELETE]" link type="danger" size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <el-dialog v-model="dialogVisible" title="新增 WBS 任务" width="720px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="任务编码" prop="taskCode"><el-input v-model="form.taskCode" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务名称" prop="taskName"><el-input v-model="form.taskName" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="项目 ID" prop="initiationId">
              <el-input-number v-model="form.initiationId" :min="1" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人 ID" prop="ownerId">
              <el-input-number v-model="form.ownerId" :min="1" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="任务类型">
              <el-select v-model="form.taskType" style="width: 100%">
                <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-select v-model="form.priority" style="width: 100%">
                <el-option v-for="(v, k) in priorityMap" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="计划开始">
              <el-date-picker v-model="form.plannedStartDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划结束">
              <el-date-picker v-model="form.plannedEndDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="计划工时">
              <el-input-number v-model="form.plannedEffort" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="依赖任务">
              <el-input v-model="form.dependsOn" placeholder="如: 100,101" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="交付物">
          <el-input v-model="form.deliverable" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
