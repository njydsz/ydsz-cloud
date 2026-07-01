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
import { ref, reactive, onMounted } from 'vue'
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
const statusMap = {
  DRAFT: { label: '草稿', type: 'info' as const },
  SUBMITTED: { label: '已提交', type: 'warning' as const },
  APPROVED: { label: '已通过', type: 'success' as const },
  REJECTED: { label: '已驳回', type: 'danger' as const },
}

// 工作类型字典：常规/加班/培训/请假
const workTypeMap = {
  REGULAR: { label: '常规' },
  OVERTIME: { label: '加班' },
  TRAINING: { label: '培训' },
  LEAVE: { label: '请假' },
}

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

const formRules = {
  entryDate: [{ required: true, message: '日期必填', trigger: 'change' }],
  employeeId: [{ required: true, message: '员工 ID 必填', trigger: 'blur' }],
  initiationId: [{ required: true, message: '项目 ID 必填', trigger: 'blur' }],
  hours: [{ required: true, message: '工时必填', trigger: 'blur' }],
}

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
  ElMessage.success('已提交（待审批）')
  dialogVisible.value = false
  fetchList()
}

/** 审批通过：二次确认后推进状态，通过后自动触发成本分摊 */
async function handleApprove(row: TimeEntryVO) {
  try {
    await ElMessageBox.confirm(`确认通过该工时记录？审批通过将自动触发成本分摊。`, '提示', { type: 'warning' })
    await approveTimeEntry({ id: row.id, approverId: 1, approverName: '系统' })
    ElMessage.success('已通过')
    fetchList()
  } catch { /* 取消 */ }
}

/** 审批驳回：需输入驳回原因（必填）后推进状态 */
async function handleReject(row: TimeEntryVO) {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回工时', { inputValidator: (v) => !!v || '原因必填' })
    await rejectTimeEntry({ id: row.id, approverId: 1, approverName: '系统', reason: value })
    ElMessage.success('已驳回')
    fetchList()
  } catch { /* 取消 */ }
}

/** 删除工时记录（二次确认） */
async function handleDelete(row: TimeEntryVO) {
  try {
    await ElMessageBox.confirm(`确认删除该工时记录？`, '提示', { type: 'warning' })
    await deleteTimeEntry(row.id)
    ElMessage.success('删除成功')
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
      <el-form-item label="关键字"><el-input v-model="query.keyword" placeholder="员工/项目" clearable /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="员工 ID"><el-input-number v-model="query.employeeId" :min="0" :controls="false" /></el-form-item>
      <el-form-item label="项目 ID"><el-input-number v-model="query.initiationId" :min="0" :controls="false" /></el-form-item>
      <el-form-item label="日期">
        <el-date-picker
          v-model="query.startDate"
          type="daterange"
          range-separator="至"
          start-placeholder="开始"
          end-placeholder="结束"
          value-format="YYYY-MM-DD"
          unlink-panels
        />
      </el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.EXECUTION_TIME_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        填写工时
      </el-button>
    </template>

    <template #table>
      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="entryDate" title="日期" width="110" />
        <vxe-column field="employeeName" title="员工" width="100" />
        <vxe-column field="levelCode" title="职级" width="80" align="center" />
        <vxe-column field="initiationName" title="项目" width="160" show-overflow />
        <vxe-column field="taskName" title="任务" width="140" show-overflow />
        <vxe-column field="hours" title="工时(h)" width="90" align="right" />
        <vxe-column field="overtime" title="加班(h)" width="90" align="right" />
        <vxe-column field="workType" title="类型" width="80" align="center">
          <template #default="{ row }">{{ workTypeMap[row.workType as keyof typeof workTypeMap]?.label || row.workType || '-' }}</template>
        </vxe-column>
        <vxe-column field="status" title="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column field="approverName" title="审批人" width="100" />
        <vxe-column field="description" title="说明" min-width="180" show-overflow />
        <vxe-column title="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.EXECUTION_TIME_APPROVE]" link type="success" size="small" @click="handleApprove(row)">通过</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.EXECUTION_TIME_REJECT]" link type="danger" size="small" @click="handleReject(row)">驳回</el-button>
            <el-button v-if="['DRAFT', 'REJECTED'].includes(row.status || '')" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <el-dialog v-model="dialogVisible" title="填写工时" width="520px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="日期" prop="entryDate">
          <el-date-picker v-model="form.entryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="员工 ID" prop="employeeId">
          <el-input-number v-model="form.employeeId" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="职级">
          <el-input v-model="form.levelCode" placeholder="如: L8" />
        </el-form-item>
        <el-form-item label="项目 ID" prop="initiationId">
          <el-input-number v-model="form.initiationId" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="任务 ID">
          <el-input-number v-model="form.taskId" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="工时(h)" prop="hours">
          <el-input-number v-model="form.hours" :min="0" :max="24" :step="0.5" style="width: 100%" />
        </el-form-item>
        <el-form-item label="加班(h)">
          <el-input-number v-model="form.overtime" :min="0" :max="24" :step="0.5" style="width: 100%" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.workType" style="width: 100%">
            <el-option v-for="(v, k) in workTypeMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
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
