<!--
  @fileoverview 分布式任务引擎 - 任务管理页面
  @description 任务调度的核心管控页面：
  - 顶部筛选栏：jobKey / status / jobGroup
  - 批量操作工具栏：批量暂停/恢复/触发/删除
  - 任务列表：任务名称/jobKey/分组/类型/调度类型/Cron/状态/下次触发/累计触发/成功率/操作
  - 操作列：编辑/暂停|恢复/触发/删除/查看日志/GLUE代码/历史版本
  - 新建/编辑对话框：FormDialog 组件，scheduleType 控制不同字段显示
  @module views/cronjob
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  getJobPage,
  createJob,
  updateJob,
  deleteJob,
  pauseJob,
  resumeJob,
  triggerJob,
  batchPauseJobs,
  batchResumeJobs,
  batchTriggerJobs,
  batchDeleteJobs,
} from '@/api/cronjob'
import type {
  JobVO,
  JobSaveDTO,
  JobPageQuery,
  JobStatus,
  ScheduleType,
  JobType,
} from '@/api/cronjob/types'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()
const router = useRouter()

/** 查询参数 */
const query = reactive<JobPageQuery>({
  page: 1,
  size: 10,
  jobKey: undefined,
  status: undefined,
  jobGroup: undefined,
})

/** 列表数据 */
const list = ref<JobVO[]>([])
/** 总数 */
const total = ref(0)
/** 加载中 */
const loading = ref(false)
/** 选中行 */
const selectedRows = ref<JobVO[]>([])

/** Element Plus el-tag type 联合类型 */
type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary'

/** 任务状态选项 */
const statusOptions: { label: string; value: JobStatus }[] = [
  { label: t('cronjob.statusNormal'), value: 'NORMAL' },
  { label: t('cronjob.statusPaused'), value: 'PAUSED' },
  { label: t('cronjob.statusError'), value: 'ERROR' },
  { label: t('cronjob.statusAutoPaused'), value: 'AUTO_PAUSED' },
  { label: t('cronjob.statusComplete'), value: 'COMPLETE' },
]

/** 调度类型选项 */
const scheduleTypeOptions: { label: string; value: ScheduleType }[] = [
  { label: t('cronjob.scheduleCron'), value: 'CRON' },
  { label: t('cronjob.scheduleFixedRate'), value: 'FIXED_RATE' },
  { label: t('cronjob.scheduleFixedDelay'), value: 'FIXED_DELAY' },
  { label: t('cronjob.scheduleApi'), value: 'API' },
]

/** 任务类型选项 */
const jobTypeOptions: { label: string; value: JobType }[] = [
  { label: 'BEAN', value: 'BEAN' },
  { label: 'GLUE', value: 'GLUE' },
  { label: 'MAPREDUCE', value: 'MAPREDUCE' },
]

/** 阻塞策略选项 */
const blockStrategyOptions = [
  { label: 'SERIAL_EXECUTION', value: 'SERIAL_EXECUTION' },
  { label: 'DISCARD_LATER', value: 'DISCARD_LATER' },
  { label: 'COVER_EARLY', value: 'COVER_EARLY' },
]

/** Misfire 策略选项 */
const misfirePolicyOptions = [
  { label: 'FIRE_ONCE', value: 'FIRE_ONCE' },
  { label: 'IGNORE', value: 'IGNORE' },
  { label: 'FIRE_AND_PROCEED', value: 'FIRE_AND_PROCEED' },
]

/** 退避策略选项 */
const retryBackoffOptions = [
  { label: 'FIXED', value: 'FIXED' },
  { label: 'LINEAR', value: 'LINEAR' },
  { label: 'EXPONENTIAL', value: 'EXPONENTIAL' },
]

/** 任务状态 Tag 类型映射 */
const statusTagType: Record<JobStatus, TagType> = {
  NORMAL: 'success',
  PAUSED: 'info',
  ERROR: 'danger',
  AUTO_PAUSED: 'warning',
  COMPLETE: 'info',
}

/** 任务状态文案映射 */
const statusLabelMap: Record<JobStatus, string> = {
  NORMAL: t('cronjob.statusNormal'),
  PAUSED: t('cronjob.statusPaused'),
  ERROR: t('cronjob.statusError'),
  AUTO_PAUSED: t('cronjob.statusAutoPaused'),
  COMPLETE: t('cronjob.statusComplete'),
}

/** 是否有选中行 */
const hasSelection = computed(() => selectedRows.value.length > 0)

// ==================== 弹窗表单 ====================

/** 弹窗显示 */
const dialogVisible = ref(false)
/** 弹窗标题 */
const dialogTitle = ref('')
/** 提交中 */
const submitting = ref(false)
/** 是否编辑模式 */
const isEdit = ref(false)

/** FormDialog 组件暴露的实例方法（对齐 components/common/FormDialog.vue 的 defineExpose） */
interface FormDialogInstance {
  validate: () => Promise<boolean>
  clearValidate: () => void
  resetFields: () => void
}

/** 表单引用（FormDialog 暴露的 formRef） */
const formDialogRef = ref<FormDialogInstance | null>(null)

/** 表单数据 */
const form = reactive<JobSaveDTO>({
  id: undefined,
  jobName: '',
  jobKey: '',
  jobGroup: '',
  handler: '',
  scheduleType: 'CRON',
  cronExpression: '',
  fixedRateMs: undefined,
  fixedDelayMs: undefined,
  jobType: 'BEAN',
  paramsJson: '',
  remark: '',
  shardTotal: 1,
  misfirePolicy: 'FIRE_ONCE',
  blockStrategy: 'SERIAL_EXECUTION',
  maxRetries: 0,
  retryIntervalMs: 1000,
  retryBackoff: 'FIXED',
  maxConsecutiveFails: 5,
  autoResumeAfterMinutes: undefined,
  priority: 0,
  timezone: 'Asia/Shanghai',
  lockTtlMs: 60000,
  timeoutMs: 0,
  slowThresholdMs: 0,
})

/** 表单校验规则 */
const formRules = {
  jobName: [{ required: true, message: t('cronjob.jobName'), trigger: 'blur' }],
  jobKey: [{ required: true, message: t('cronjob.jobKey'), trigger: 'blur' }],
  scheduleType: [{ required: true, message: t('cronjob.scheduleType'), trigger: 'change' }],
  jobType: [{ required: true, message: t('cronjob.jobType'), trigger: 'change' }],
}

/** 拉取列表 */
const fetchList = async () => {
  loading.value = true
  try {
    const resp = await getJobPage(query)
    list.value = resp.data?.records ?? []
    total.value = resp.data?.total ?? 0
  } catch {
    // 静默失败
  } finally {
    loading.value = false
  }
}

/** 搜索 */
const handleSearch = () => {
  query.page = 1
  fetchList()
}

/** 重置 */
const handleReset = () => {
  query.jobKey = undefined
  query.status = undefined
  query.jobGroup = undefined
  query.page = 1
  fetchList()
}

/** 翻页 */
const handlePageChange = (page: number) => {
  query.page = page
  fetchList()
}

/** 每页条数变化 */
const handleSizeChange = (size: number) => {
  query.size = size
  query.page = 1
  fetchList()
}

/** 行选择变化 */
const handleSelectionChange = (rows: JobVO[]) => {
  selectedRows.value = rows
}

/** 计算成功率 */
const calcSuccessRate = (row: JobVO): string => {
  const fire = row.fireCount ?? 0
  if (fire === 0) return '-'
  const success = row.successCount ?? 0
  return `${((success / fire) * 100).toFixed(1)}%`
}

/** 重置表单 */
const resetForm = () => {
  form.id = undefined
  form.jobName = ''
  form.jobKey = ''
  form.jobGroup = ''
  form.handler = ''
  form.scheduleType = 'CRON'
  form.cronExpression = ''
  form.fixedRateMs = undefined
  form.fixedDelayMs = undefined
  form.jobType = 'BEAN'
  form.paramsJson = ''
  form.remark = ''
  form.shardTotal = 1
  form.misfirePolicy = 'FIRE_ONCE'
  form.blockStrategy = 'SERIAL_EXECUTION'
  form.maxRetries = 0
  form.retryIntervalMs = 1000
  form.retryBackoff = 'FIXED'
  form.maxConsecutiveFails = 5
  form.autoResumeAfterMinutes = undefined
  form.priority = 0
  form.timezone = 'Asia/Shanghai'
  form.lockTtlMs = 60000
  form.timeoutMs = 0
  form.slowThresholdMs = 0
}

/** 打开新建弹窗 */
const handleCreate = () => {
  isEdit.value = false
  dialogTitle.value = t('cronjob.create')
  resetForm()
  dialogVisible.value = true
}

/** 打开编辑弹窗 */
const handleEdit = (row: JobVO) => {
  isEdit.value = true
  dialogTitle.value = t('cronjob.edit')
  resetForm()
  form.id = row.id
  form.jobName = row.jobName
  form.jobKey = row.jobKey
  form.jobGroup = row.jobGroup
  form.handler = row.handler
  form.scheduleType = row.scheduleType
  form.cronExpression = row.cronExpression
  form.fixedRateMs = row.fixedRateMs
  form.fixedDelayMs = row.fixedDelayMs
  form.jobType = row.jobType
  form.paramsJson = row.paramsJson
  form.remark = row.remark
  form.shardTotal = row.shardTotal
  form.misfirePolicy = row.misfirePolicy
  form.blockStrategy = row.blockStrategy
  form.maxRetries = row.maxRetries
  form.retryIntervalMs = row.retryIntervalMs
  form.retryBackoff = row.retryBackoff
  form.maxConsecutiveFails = row.maxConsecutiveFails
  form.autoResumeAfterMinutes = row.autoResumeAfterMinutes
  form.priority = row.priority
  form.timezone = row.timezone
  form.lockTtlMs = row.lockTtlMs
  form.timeoutMs = row.timeoutMs
  form.slowThresholdMs = row.slowThresholdMs
  dialogVisible.value = true
}

/** 提交表单 */
const handleSubmit = async () => {
  const valid = await formDialogRef.value?.validate()
  if (!valid) return
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateJob(form)
      ElMessage.success(t('cronjob.saveSuccess'))
    } else {
      await createJob(form)
      ElMessage.success(t('cronjob.createSuccess'))
    }
    dialogVisible.value = false
    fetchList()
  } catch {
    // 静默失败
  } finally {
    submitting.value = false
  }
}

/** 暂停任务 */
const handlePause = async (row: JobVO) => {
  try {
    await ElMessageBox.confirm(t('cronjob.pauseConfirm'), t('common.confirm'), {
      type: 'warning',
    })
    await pauseJob(row.id)
    ElMessage.success(t('cronjob.pauseSuccess'))
    fetchList()
  } catch {
    // 用户取消或请求失败
  }
}

/** 恢复任务 */
const handleResume = async (row: JobVO) => {
  try {
    await ElMessageBox.confirm(t('cronjob.resumeConfirm'), t('common.confirm'), {
      type: 'warning',
    })
    await resumeJob(row.id)
    ElMessage.success(t('cronjob.resumeSuccess'))
    fetchList()
  } catch {
    // 用户取消或请求失败
  }
}

/** 触发任务 */
const handleTrigger = async (row: JobVO) => {
  try {
    await ElMessageBox.confirm(t('cronjob.triggerConfirm'), t('common.confirm'), {
      type: 'warning',
    })
    await triggerJob(row.id)
    ElMessage.success(t('cronjob.triggerSuccess'))
    fetchList()
  } catch {
    // 用户取消或请求失败
  }
}

/** 删除任务 */
const handleDelete = async (row: JobVO) => {
  try {
    await ElMessageBox.confirm(t('cronjob.deleteConfirm'), t('common.confirm'), {
      type: 'warning',
    })
    await deleteJob(row.id)
    ElMessage.success(t('cronjob.deleteSuccess'))
    fetchList()
  } catch {
    // 用户取消或请求失败
  }
}

/** 批量暂停 */
const handleBatchPause = async () => {
  if (!hasSelection.value) return
  const n = selectedRows.value.length
  try {
    await ElMessageBox.confirm(t('cronjob.batchPauseConfirm', { n }), t('common.confirm'), {
      type: 'warning',
    })
    await batchPauseJobs({ jobIds: selectedRows.value.map((r) => r.id) })
    ElMessage.success(t('cronjob.pauseSuccess'))
    fetchList()
  } catch {
    // 用户取消或请求失败
  }
}

/** 批量恢复 */
const handleBatchResume = async () => {
  if (!hasSelection.value) return
  const n = selectedRows.value.length
  try {
    await ElMessageBox.confirm(t('cronjob.batchResumeConfirm', { n }), t('common.confirm'), {
      type: 'warning',
    })
    await batchResumeJobs({ jobIds: selectedRows.value.map((r) => r.id) })
    ElMessage.success(t('cronjob.resumeSuccess'))
    fetchList()
  } catch {
    // 用户取消或请求失败
  }
}

/** 批量触发 */
const handleBatchTrigger = async () => {
  if (!hasSelection.value) return
  const n = selectedRows.value.length
  try {
    await ElMessageBox.confirm(t('cronjob.batchTriggerConfirm', { n }), t('common.confirm'), {
      type: 'warning',
    })
    await batchTriggerJobs({ jobIds: selectedRows.value.map((r) => r.id) })
    ElMessage.success(t('cronjob.triggerSuccess'))
    fetchList()
  } catch {
    // 用户取消或请求失败
  }
}

/** 批量删除 */
const handleBatchDelete = async () => {
  if (!hasSelection.value) return
  const n = selectedRows.value.length
  try {
    await ElMessageBox.confirm(t('cronjob.batchDeleteConfirm', { n }), t('common.confirm'), {
      type: 'warning',
    })
    await batchDeleteJobs({ jobIds: selectedRows.value.map((r) => r.id) })
    ElMessage.success(t('cronjob.deleteSuccess'))
    fetchList()
  } catch {
    // 用户取消或请求失败
  }
}

/** 查看日志（跳转日志页并按 jobId 筛选） */
const handleViewLog = (row: JobVO) => {
  router.push({ path: '/cronjob/log', query: { jobId: row.id } })
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="cronjob-list">
    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent="handleSearch">
        <el-form-item :label="t('cronjob.jobKey')">
          <el-input
            v-model="query.jobKey"
            :placeholder="t('cronjob.jobKey')"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item :label="t('cronjob.status')">
          <el-select
            v-model="query.status"
            :placeholder="t('common.all')"
            clearable
            style="width: 160px"
          >
            <el-option
              v-for="opt in statusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('cronjob.jobGroup')">
          <el-input
            v-model="query.jobGroup"
            :placeholder="t('cronjob.jobGroup')"
            clearable
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button
          v-permission="PC.CRONJOB_JOB_CREATE"
          type="primary"
          @click="handleCreate"
        >
          {{ t('cronjob.create') }}
        </el-button>
        <el-button
          v-permission="PC.CRONJOB_JOB_BATCH"
          type="warning"
          plain
          :disabled="!hasSelection"
          @click="handleBatchPause"
        >
          {{ t('cronjob.batchPause') }}
        </el-button>
        <el-button
          v-permission="PC.CRONJOB_JOB_BATCH"
          type="success"
          plain
          :disabled="!hasSelection"
          @click="handleBatchResume"
        >
          {{ t('cronjob.batchResume') }}
        </el-button>
        <el-button
          v-permission="PC.CRONJOB_JOB_BATCH"
          type="primary"
          plain
          :disabled="!hasSelection"
          @click="handleBatchTrigger"
        >
          {{ t('cronjob.batchTrigger') }}
        </el-button>
        <el-button
          v-permission="PC.CRONJOB_JOB_BATCH"
          type="danger"
          plain
          :disabled="!hasSelection"
          @click="handleBatchDelete"
        >
          {{ t('cronjob.batchDelete') }}
        </el-button>
      </div>
      <span class="total-text">{{ t('cronjob.total', { n: total }) }}</span>
    </div>

    <!-- 列表 -->
    <el-table
      v-loading="loading"
      :data="list"
      style="width: 100%"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="45" />
      <el-table-column :label="t('cronjob.jobName')" min-width="160" show-overflow-tooltip>
        <template #default="scope">
          {{ (scope.row as JobVO).jobName }}
        </template>
      </el-table-column>
      <el-table-column :label="t('cronjob.jobKey')" min-width="160" show-overflow-tooltip>
        <template #default="scope">
          {{ (scope.row as JobVO).jobKey }}
        </template>
      </el-table-column>
      <el-table-column :label="t('cronjob.jobGroup')" width="120" show-overflow-tooltip>
        <template #default="scope">
          {{ (scope.row as JobVO).jobGroup || '-' }}
        </template>
      </el-table-column>
      <el-table-column :label="t('cronjob.jobType')" width="110">
        <template #default="scope">
          <el-tag size="small" type="info">{{ (scope.row as JobVO).jobType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('cronjob.scheduleType')" width="120">
        <template #default="scope">
          {{ (scope.row as JobVO).scheduleType }}
        </template>
      </el-table-column>
      <el-table-column :label="t('cronjob.cronExpression')" width="170" show-overflow-tooltip>
        <template #default="scope">
          {{ (scope.row as JobVO).cronExpression || '-' }}
        </template>
      </el-table-column>
      <el-table-column :label="t('cronjob.status')" width="100">
        <template #default="scope">
          <el-tag :type="statusTagType[(scope.row as JobVO).status]" size="small">
            {{ statusLabelMap[(scope.row as JobVO).status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('cronjob.nextFireTime')" width="170">
        <template #default="scope">
          {{ (scope.row as JobVO).nextFireTime || '-' }}
        </template>
      </el-table-column>
      <el-table-column :label="t('cronjob.fireCount')" width="90">
        <template #default="scope">
          {{ (scope.row as JobVO).fireCount ?? 0 }}
        </template>
      </el-table-column>
      <el-table-column :label="t('cronjob.successRate')" width="100">
        <template #default="scope">
          {{ calcSuccessRate(scope.row as JobVO) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="scope">
          <el-button
            v-permission="PC.CRONJOB_JOB_UPDATE"
            link
            type="primary"
            size="small"
            @click.stop="handleEdit(scope.row as JobVO)"
          >
            {{ t('cronjob.edit') }}
          </el-button>
          <el-button
            v-if="(scope.row as JobVO).status === 'NORMAL' || (scope.row as JobVO).status === 'ERROR'"
            v-permission="PC.CRONJOB_JOB_TRIGGER"
            link
            type="warning"
            size="small"
            @click.stop="handlePause(scope.row as JobVO)"
          >
            {{ t('cronjob.pause') }}
          </el-button>
          <el-button
            v-if="(scope.row as JobVO).status === 'PAUSED' || (scope.row as JobVO).status === 'AUTO_PAUSED'"
            v-permission="PC.CRONJOB_JOB_TRIGGER"
            link
            type="success"
            size="small"
            @click.stop="handleResume(scope.row as JobVO)"
          >
            {{ t('cronjob.resume') }}
          </el-button>
          <el-button
            v-permission="PC.CRONJOB_JOB_TRIGGER"
            link
            type="primary"
            size="small"
            @click.stop="handleTrigger(scope.row as JobVO)"
          >
            {{ t('cronjob.trigger') }}
          </el-button>
          <el-button
            link
            type="info"
            size="small"
            @click.stop="handleViewLog(scope.row as JobVO)"
          >
            {{ t('cronjob.viewLog') }}
          </el-button>
          <el-button
            v-permission="PC.CRONJOB_JOB_DELETE"
            link
            type="danger"
            size="small"
            @click.stop="handleDelete(scope.row as JobVO)"
          >
            {{ t('cronjob.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 新建/编辑对话框 -->
    <FormDialog
      ref="formDialogRef"
      v-model="dialogVisible"
      :title="dialogTitle"
      width="780px"
      :loading="submitting"
      :close-on-click-modal="false"
      @submit="handleSubmit"
    >
      <el-form :model="form" :rules="formRules" label-width="140px" label-position="right">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('cronjob.jobName')" prop="jobName">
              <el-input v-model="form.jobName" :placeholder="t('cronjob.jobName')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('cronjob.jobKey')" prop="jobKey">
              <el-input v-model="form.jobKey" :placeholder="t('cronjob.jobKey')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('cronjob.jobGroup')">
              <el-input v-model="form.jobGroup" :placeholder="t('cronjob.jobGroup')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('cronjob.jobType')" prop="jobType">
              <el-select v-model="form.jobType" style="width: 100%">
                <el-option
                  v-for="opt in jobTypeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('cronjob.handler')">
          <el-input v-model="form.handler" :placeholder="t('cronjob.handler')" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('cronjob.scheduleType')" prop="scheduleType">
              <el-select v-model="form.scheduleType" style="width: 100%">
                <el-option
                  v-for="opt in scheduleTypeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="form.scheduleType === 'CRON'" :span="12">
            <el-form-item :label="t('cronjob.cronExpression')">
              <el-input v-model="form.cronExpression" placeholder="0 0/5 * * * ?" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.scheduleType === 'FIXED_RATE'" :span="12">
            <el-form-item :label="t('cronjob.fixedRateMs')">
              <el-input-number v-model="form.fixedRateMs" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.scheduleType === 'FIXED_DELAY'" :span="12">
            <el-form-item :label="t('cronjob.fixedDelayMs')">
              <el-input-number v-model="form.fixedDelayMs" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('cronjob.paramsJson')">
          <el-input
            v-model="form.paramsJson"
            type="textarea"
            :rows="2"
            :placeholder="t('cronjob.paramsJson')"
          />
        </el-form-item>
        <el-form-item :label="t('cronjob.remark')">
          <el-input v-model="form.remark" :placeholder="t('cronjob.remark')" />
        </el-form-item>
        <el-divider content-position="left">{{ t('cronjob.title') }}</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('cronjob.shardTotal')">
              <el-input-number v-model="form.shardTotal" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('cronjob.priority')">
              <el-input-number v-model="form.priority" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('cronjob.misfirePolicy')">
              <el-select v-model="form.misfirePolicy" style="width: 100%">
                <el-option
                  v-for="opt in misfirePolicyOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('cronjob.blockStrategy')">
              <el-select v-model="form.blockStrategy" style="width: 100%">
                <el-option
                  v-for="opt in blockStrategyOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('cronjob.maxRetries')">
              <el-input-number v-model="form.maxRetries" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('cronjob.retryIntervalMs')">
              <el-input-number v-model="form.retryIntervalMs" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('cronjob.retryBackoff')">
              <el-select v-model="form.retryBackoff" style="width: 100%">
                <el-option
                  v-for="opt in retryBackoffOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('cronjob.maxConsecutiveFails')">
              <el-input-number v-model="form.maxConsecutiveFails" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('cronjob.autoResumeAfterMinutes')">
              <el-input-number v-model="form.autoResumeAfterMinutes" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('cronjob.timezone')">
              <el-input v-model="form.timezone" placeholder="Asia/Shanghai" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('cronjob.lockTtlMs')">
              <el-input-number v-model="form.lockTtlMs" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('cronjob.timeoutMs')">
              <el-input-number v-model="form.timeoutMs" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('cronjob.slowThresholdMs')">
              <el-input-number v-model="form.slowThresholdMs" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </FormDialog>
  </div>
</template>

<style lang="scss" scoped>
.cronjob-list {
  padding: 16px;
}

.filter-card {
  margin-bottom: 16px;

  :deep(.el-card__body) {
    padding-bottom: 2px;
  }
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;

  .toolbar-left {
    display: flex;
    gap: 12px;
    align-items: center;
    flex-wrap: wrap;
  }

  .total-text {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
