<!--
  @file WBS 任务管理
  @description WBS 任务管理页面：支持任务分页查询、新建、状态流转(PLANNED→IN_PROGRESS→BLOCKED/IN_REVIEW→COMPLETED/CANCELLED)、进度更新，对应路由 /execution/wbs-task
  @module views/execution/wbs-task
-->
<script setup lang="ts">
/**
 * WBS 任务管理
 *
 * 状态: PLANNED -> IN_PROGRESS -> BLOCKED -> IN_REVIEW -> COMPLETED/CANCELLED
 */
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()

// 列表查询状态
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

// 状态字典：任务全生命周期状态映射到标签文案与色值
const statusMap = computed(() => ({
  PLANNED: { label: t('execution.wbsTask.status.PLANNED'), type: 'info' as const },
  IN_PROGRESS: { label: t('execution.wbsTask.status.IN_PROGRESS'), type: 'primary' as const },
  BLOCKED: { label: t('execution.wbsTask.status.BLOCKED'), type: 'danger' as const },
  IN_REVIEW: { label: t('execution.wbsTask.status.IN_REVIEW'), type: 'warning' as const },
  COMPLETED: { label: t('execution.wbsTask.status.COMPLETED'), type: 'success' as const },
  CANCELLED: { label: t('execution.wbsTask.status.CANCELLED'), type: 'info' as const },
}))

// 优先级字典：低/普通/高/紧急
const priorityMap = computed(() => ({
  LOW: { label: t('execution.wbsTask.priority.LOW'), type: 'info' as const },
  NORMAL: { label: t('execution.wbsTask.priority.NORMAL'), type: 'primary' as const },
  HIGH: { label: t('execution.wbsTask.priority.HIGH'), type: 'warning' as const },
  URGENT: { label: t('execution.wbsTask.priority.URGENT'), type: 'danger' as const },
}))

// 任务类型字典：任务/里程碑/汇总
const typeMap = computed(() => ({
  TASK: { label: t('execution.wbsTask.taskType.TASK') },
  MILESTONE: { label: t('execution.wbsTask.taskType.MILESTONE') },
  SUMMARY: { label: t('execution.wbsTask.taskType.SUMMARY') },
}))

/** 拉取 WBS 任务分页数据 */
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

/** 重置查询条件并刷新列表 */
function handleReset() {
  query.keyword = ''
  query.status = ''
  query.initiationId = undefined
  query.ownerId = undefined
  query.page = 1
  fetchList()
}

// 弹窗 - 新建 WBS 任务
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

const formRules = computed(() => ({
  taskCode: [{ required: true, message: t('execution.wbsTask.rules.taskCodeRequired'), trigger: 'blur' }],
  taskName: [{ required: true, message: t('execution.wbsTask.rules.taskNameRequired'), trigger: 'blur' }],
  initiationId: [{ required: true, message: t('execution.wbsTask.rules.initiationIdRequired'), trigger: 'blur' }],
  ownerId: [{ required: true, message: t('execution.wbsTask.rules.ownerIdRequired'), trigger: 'blur' }],
}))

/** 打开新建弹窗：重置表单为默认值 */
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

/** 提交新建表单：校验通过后调用创建接口 */
async function submitForm() {
  await formRef.value?.validate()
  await createWbsTask(form as WbsTaskCreateDTO)
  ElMessage.success(t('execution.wbsTask.messages.createSuccess'))
  dialogVisible.value = false
  fetchList()
}

/** 删除任务（二次确认） */
async function handleDelete(row: WbsTaskVO) {
  try {
    await ElMessageBox.confirm(t('execution.wbsTask.messages.confirmDelete', { name: row.taskName }), t('common.confirm'), { type: 'warning' })
    await deleteWbsTask(row.id)
    ElMessage.success(t('execution.wbsTask.messages.deleteSuccess'))
    fetchList()
  } catch { /* 取消 */ }
}

/** 状态流转：根据目标状态推进任务流程（启动/提评审/阻塞/解除阻塞/完成） */
async function handleStatus(row: WbsTaskVO, target: string) {
  const targetText = statusMap.value[target as keyof typeof statusMap.value]?.label || target
  try {
    await ElMessageBox.confirm(t('execution.wbsTask.messages.confirmStatusChange', { target: targetText }), t('common.confirm'), { type: 'warning' })
    await changeWbsTaskStatus({ id: row.id, targetStatus: target })
    ElMessage.success(t('execution.wbsTask.messages.statusUpdated'))
    fetchList()
  } catch { /* 取消 */ }
}

/** 更新任务进度：输入 0-100 的百分比，校验后调用状态变更接口带 progressPct */
async function handleProgress(row: WbsTaskVO) {
  try {
    const { value } = await ElMessageBox.prompt(t('execution.wbsTask.messages.progressPrompt'), t('execution.wbsTask.messages.progressTitle'), {
      inputPattern: /^\d+(\.\d{1,2})?$/,
      inputErrorMessage: t('execution.wbsTask.messages.progressError'),
    })
    const pct = Number(value)
    if (pct < 0 || pct > 100) {
      ElMessage.warning(t('execution.wbsTask.messages.progressRange'))
      return
    }
    await changeWbsTaskStatus({ id: row.id, targetStatus: 'IN_PROGRESS', progressPct: pct })
    ElMessage.success(t('execution.wbsTask.messages.progressUpdated'))
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
      <el-form-item :label="$t('execution.wbsTask.search.keyword')">
        <el-input v-model="query.keyword" :placeholder="$t('execution.wbsTask.search.keywordPlaceholder')" clearable />
      </el-form-item>
      <el-form-item :label="$t('execution.wbsTask.search.status')">
        <el-select v-model="query.status" :placeholder="$t('common.all')" clearable style="width: 140px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('execution.wbsTask.search.initiationId')">
        <el-input-number v-model="query.initiationId" :min="0" :controls="false" />
      </el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.EXECUTION_WBS_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        {{ $t('execution.wbsTask.buttons.create') }}
      </el-button>
    </template>

    <template #table>
      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="taskCode" :title="$t('execution.wbsTask.columns.taskCode')" width="140" />
        <vxe-column field="taskName" :title="$t('execution.wbsTask.columns.taskName')" min-width="200" show-overflow />
        <vxe-column field="initiationName" :title="$t('execution.wbsTask.columns.initiationName')" width="160" show-overflow />
        <vxe-column field="taskType" :title="$t('execution.wbsTask.columns.taskType')" width="80" align="center">
          <template #default="{ row }">{{ typeMap[row.taskType as keyof typeof typeMap]?.label || row.taskType || '-' }}</template>
        </vxe-column>
        <vxe-column field="priority" :title="$t('execution.wbsTask.columns.priority')" width="80" align="center">
          <template #default="{ row }"><StatusTag :value="row.priority" :map="priorityMap" /></template>
        </vxe-column>
        <vxe-column field="ownerName" :title="$t('execution.wbsTask.columns.ownerName')" width="100" />
        <vxe-column field="plannedStartDate" :title="$t('execution.wbsTask.columns.plannedStartDate')" width="110" />
        <vxe-column field="plannedEndDate" :title="$t('execution.wbsTask.columns.plannedEndDate')" width="110" />
        <vxe-column field="plannedEffort" :title="$t('execution.wbsTask.columns.plannedEffort')" width="100" align="right" />
        <vxe-column field="progressPct" :title="$t('execution.wbsTask.columns.progressPct')" width="180">
          <template #default="{ row }">
            <el-progress :percentage="Number(row.progressPct || 0)" :stroke-width="10" />
          </template>
        </vxe-column>
        <vxe-column field="status" :title="$t('execution.wbsTask.columns.status')" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column :title="$t('execution.wbsTask.columns.action')" width="320" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PLANNED'" v-permission="[PC.EXECUTION_WBS_STATUS]" link type="primary" size="small" @click="handleStatus(row, 'IN_PROGRESS')">
              {{ $t('execution.wbsTask.buttons.start') }}
            </el-button>
            <el-button v-if="row.status === 'IN_PROGRESS'" v-permission="[PC.EXECUTION_WBS_STATUS]" link type="primary" size="small" @click="handleProgress(row)">
              {{ $t('execution.wbsTask.buttons.updateProgress') }}
            </el-button>
            <el-button v-if="row.status === 'IN_PROGRESS'" v-permission="[PC.EXECUTION_WBS_STATUS]" link type="warning" size="small" @click="handleStatus(row, 'IN_REVIEW')">
              {{ $t('execution.wbsTask.buttons.submitReview') }}
            </el-button>
            <el-button v-if="row.status === 'IN_PROGRESS'" v-permission="[PC.EXECUTION_WBS_STATUS]" link type="danger" size="small" @click="handleStatus(row, 'BLOCKED')">
              {{ $t('execution.wbsTask.buttons.block') }}
            </el-button>
            <el-button v-if="row.status === 'IN_REVIEW'" v-permission="[PC.EXECUTION_WBS_STATUS]" link type="success" size="small" @click="handleStatus(row, 'COMPLETED')">
              {{ $t('execution.wbsTask.buttons.complete') }}
            </el-button>
            <el-button v-if="row.status === 'BLOCKED'" v-permission="[PC.EXECUTION_WBS_STATUS]" link type="primary" size="small" @click="handleStatus(row, 'IN_PROGRESS')">
              {{ $t('execution.wbsTask.buttons.unblock') }}
            </el-button>
            <el-button v-permission="[PC.EXECUTION_WBS_DELETE]" link type="danger" size="small" @click="handleDelete(row)">
              {{ $t('common.delete') }}
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <el-dialog v-model="dialogVisible" :title="$t('execution.wbsTask.dialog.createTitle')" width="720px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$t('execution.wbsTask.form.taskCode')" prop="taskCode"><el-input v-model="form.taskCode" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('execution.wbsTask.form.taskName')" prop="taskName"><el-input v-model="form.taskName" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$t('execution.wbsTask.form.initiationId')" prop="initiationId">
              <el-input-number v-model="form.initiationId" :min="1" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('execution.wbsTask.form.ownerId')" prop="ownerId">
              <el-input-number v-model="form.ownerId" :min="1" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$t('execution.wbsTask.form.taskType')">
              <el-select v-model="form.taskType" style="width: 100%">
                <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('execution.wbsTask.form.priority')">
              <el-select v-model="form.priority" style="width: 100%">
                <el-option v-for="(v, k) in priorityMap" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$t('execution.wbsTask.form.plannedStartDate')">
              <el-date-picker v-model="form.plannedStartDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('execution.wbsTask.form.plannedEndDate')">
              <el-date-picker v-model="form.plannedEndDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$t('execution.wbsTask.form.plannedEffort')">
              <el-input-number v-model="form.plannedEffort" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('execution.wbsTask.form.dependsOn')">
              <el-input v-model="form.dependsOn" :placeholder="$t('execution.wbsTask.form.dependsOnPlaceholder')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$t('execution.wbsTask.form.deliverable')">
          <el-input v-model="form.deliverable" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="$t('execution.wbsTask.form.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitForm">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
