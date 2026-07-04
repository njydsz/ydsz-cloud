<!--
  @file 工时填报
  @description 工时填报管理页面：支持工时分页查询、填报(TimeEntryValidator 校验)、审批流转(DRAFT→SUBMITTED→APPROVED/REJECTED)，审批通过后自动触发成本分摊，对应路由 /execution/time-entry
  @module views/execution/time-entry
-->
<script setup lang="ts">
/**
 * 工时管理
 *
 * 状态: DRAFT -> SUBMITTED -> APPROVED / REJECTED
 * 审批通过后会自动触发成本分摊。
 */
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pageTimeEntries,
  createTimeEntry,
  approveTimeEntry,
  rejectTimeEntry,
  deleteTimeEntry,
} from '@/api/execution/time-entry'
import type { TimeEntryVO, TimeEntryCreateDTO } from '@/api/execution/time-entry/types'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

// 列表查询状态
const loading = ref(false)
const list = ref<TimeEntryVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  employeeId: undefined as number | undefined,
  initiationId: undefined as number | undefined,
  startDate: '',
  endDate: '',
})

// 状态字典：工时审批状态映射到标签文案与色值
const statusMap = computed(() => ({
  DRAFT: { label: t('execution.timeEntry.status.DRAFT'), type: 'info' as const },
  SUBMITTED: { label: t('execution.timeEntry.status.SUBMITTED'), type: 'warning' as const },
  APPROVED: { label: t('execution.timeEntry.status.APPROVED'), type: 'success' as const },
  REJECTED: { label: t('execution.timeEntry.status.REJECTED'), type: 'danger' as const },
}))

// 工作类型字典：常规/加班/培训/请假
const workTypeMap = computed(() => ({
  REGULAR: { label: t('execution.timeEntry.workType.REGULAR') },
  OVERTIME: { label: t('execution.timeEntry.workType.OVERTIME') },
  TRAINING: { label: t('execution.timeEntry.workType.TRAINING') },
  LEAVE: { label: t('execution.timeEntry.workType.LEAVE') },
}))

/** 拉取工时分页数据 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageTimeEntries(query.page, query.size, {
      keyword: query.keyword,
      status: query.status,
      employeeId: query.employeeId,
      initiationId: query.initiationId,
      startDate: query.startDate,
      endDate: query.endDate,
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
  query.employeeId = undefined
  query.initiationId = undefined
  query.startDate = ''
  query.endDate = ''
  query.page = 1
  fetchList()
}

// 弹窗 - 填写工时
const dialogVisible = ref(false)
const formRef = ref<any>()
const form = reactive<Partial<TimeEntryCreateDTO>>({
  entryDate: new Date().toISOString().slice(0, 10),
  employeeId: 0,
  levelCode: '',
  initiationId: 0,
  taskId: undefined,
  hours: 8,
  overtime: 0,
  workType: 'REGULAR',
  description: '',
})

const formRules = computed(() => ({
  entryDate: [{ required: true, message: t('execution.timeEntry.rules.entryDateRequired'), trigger: 'change' }],
  employeeId: [{ required: true, message: t('execution.timeEntry.rules.employeeIdRequired'), trigger: 'blur' }],
  initiationId: [{ required: true, message: t('execution.timeEntry.rules.initiationIdRequired'), trigger: 'blur' }],
  hours: [{ required: true, message: t('execution.timeEntry.rules.hoursRequired'), trigger: 'blur' }],
}))

/** 打开新建弹窗：重置表单为默认值，回填当前查询的员工/项目 ID */
function openCreate() {
  Object.assign(form, {
    entryDate: new Date().toISOString().slice(0, 10),
    employeeId: query.employeeId ?? 0,
    levelCode: '',
    initiationId: query.initiationId ?? 0,
    taskId: undefined,
    hours: 8,
    overtime: 0,
    workType: 'REGULAR',
    description: '',
  })
  dialogVisible.value = true
}

/** 提交新建表单：校验通过后调用创建接口（TimeEntryValidator 校验），状态为待审批 */
async function submitForm() {
  await formRef.value?.validate()
  await createTimeEntry(form as TimeEntryCreateDTO)
  ElMessage.success(t('execution.timeEntry.messages.submitSuccess'))
  dialogVisible.value = false
  fetchList()
}

/** 审批通过：二次确认后推进状态，通过后自动触发成本分摊 */
async function handleApprove(row: TimeEntryVO) {
  try {
    await ElMessageBox.confirm(t('execution.timeEntry.messages.confirmApprove'), t('common.confirm'), { type: 'warning' })
    await approveTimeEntry({ id: row.id, approverId: 1, approverName: t('execution.timeEntry.systemApprover') })
    ElMessage.success(t('execution.timeEntry.messages.approveSuccess'))
    fetchList()
  } catch { /* 取消 */ }
}

/** 审批驳回：需输入驳回原因（必填）后推进状态 */
async function handleReject(row: TimeEntryVO) {
  try {
    const { value } = await ElMessageBox.prompt(t('execution.timeEntry.messages.rejectPrompt'), t('execution.timeEntry.messages.rejectTitle'), { inputValidator: (v) => !!v || t('execution.timeEntry.messages.rejectReasonRequired') })
    await rejectTimeEntry({ id: row.id, approverId: 1, approverName: t('execution.timeEntry.systemApprover'), reason: value })
    ElMessage.success(t('execution.timeEntry.messages.rejectSuccess'))
    fetchList()
  } catch { /* 取消 */ }
}

/** 删除工时记录（二次确认） */
async function handleDelete(row: TimeEntryVO) {
  try {
    await ElMessageBox.confirm(t('execution.timeEntry.messages.confirmDelete'), t('common.confirm'), { type: 'warning' })
    await deleteTimeEntry(row.id)
    ElMessage.success(t('execution.timeEntry.messages.deleteSuccess'))
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
      <el-form-item :label="$t('execution.timeEntry.search.keyword')"><el-input v-model="query.keyword" :placeholder="$t('execution.timeEntry.search.keywordPlaceholder')" clearable /></el-form-item>
      <el-form-item :label="$t('execution.timeEntry.search.status')">
        <el-select v-model="query.status" :placeholder="$t('common.all')" clearable style="width: 140px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('execution.timeEntry.search.employeeId')"><el-input-number v-model="query.employeeId" :min="0" :controls="false" /></el-form-item>
      <el-form-item :label="$t('execution.timeEntry.search.initiationId')"><el-input-number v-model="query.initiationId" :min="0" :controls="false" /></el-form-item>
      <el-form-item :label="$t('execution.timeEntry.search.dateRange')">
        <el-date-picker
          v-model="query.startDate"
          type="daterange"
          range-separator="-"
          start-placeholder="Start"
          end-placeholder="End"
          value-format="YYYY-MM-DD"
          unlink-panels
        />
      </el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.EXECUTION_TIME_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        {{ $t('execution.timeEntry.buttons.create') }}
      </el-button>
    </template>

    <template #table="scope">
      <vxe-table :data="list" :loading="loading" border stripe :height="scope.tableProps.height" :scroll-y="scope.tableProps.scrollY">
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="entryDate" :title="$t('execution.timeEntry.columns.entryDate')" width="110" />
        <vxe-column field="employeeName" :title="$t('execution.timeEntry.columns.employeeName')" width="100" />
        <vxe-column field="levelCode" :title="$t('execution.timeEntry.columns.levelCode')" width="80" align="center" />
        <vxe-column field="initiationName" :title="$t('execution.timeEntry.columns.initiationName')" width="160" show-overflow />
        <vxe-column field="taskName" :title="$t('execution.timeEntry.columns.taskName')" width="140" show-overflow />
        <vxe-column field="hours" :title="$t('execution.timeEntry.columns.hours')" width="90" align="right" />
        <vxe-column field="overtime" :title="$t('execution.timeEntry.columns.overtime')" width="90" align="right" />
        <vxe-column field="workType" :title="$t('execution.timeEntry.columns.workType')" width="80" align="center">
          <template #default="{ row }">{{ workTypeMap[row.workType as keyof typeof workTypeMap]?.label || row.workType || '-' }}</template>
        </vxe-column>
        <vxe-column field="status" :title="$t('execution.timeEntry.columns.status')" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column field="approverName" :title="$t('execution.timeEntry.columns.approverName')" width="100" />
        <vxe-column field="description" :title="$t('execution.timeEntry.columns.description')" min-width="180" show-overflow />
        <vxe-column :title="$t('execution.timeEntry.columns.action')" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.EXECUTION_TIME_APPROVE]" link type="success" size="small" @click="handleApprove(row)">{{ $t('execution.timeEntry.buttons.approve') }}</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.EXECUTION_TIME_REJECT]" link type="danger" size="small" @click="handleReject(row)">{{ $t('execution.timeEntry.buttons.reject') }}</el-button>
            <el-button v-if="['DRAFT', 'REJECTED'].includes(row.status || '')" link type="danger" size="small" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <el-dialog v-model="dialogVisible" :title="$t('execution.timeEntry.dialog.createTitle')" width="520px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item :label="$t('execution.timeEntry.form.entryDate')" prop="entryDate">
          <el-date-picker v-model="form.entryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('execution.timeEntry.form.employeeId')" prop="employeeId">
          <el-input-number v-model="form.employeeId" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('execution.timeEntry.form.levelCode')">
          <el-input v-model="form.levelCode" :placeholder="$t('execution.timeEntry.form.levelCodePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('execution.timeEntry.form.initiationId')" prop="initiationId">
          <el-input-number v-model="form.initiationId" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('execution.timeEntry.form.taskId')">
          <el-input-number v-model="form.taskId" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('execution.timeEntry.form.hours')" prop="hours">
          <el-input-number v-model="form.hours" :min="0" :max="24" :step="0.5" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('execution.timeEntry.form.overtime')">
          <el-input-number v-model="form.overtime" :min="0" :max="24" :step="0.5" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('execution.timeEntry.form.workType')">
          <el-select v-model="form.workType" style="width: 100%">
            <el-option v-for="(v, k) in workTypeMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('execution.timeEntry.form.description')">
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
