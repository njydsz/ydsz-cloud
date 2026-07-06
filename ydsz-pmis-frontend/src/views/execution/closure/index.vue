<!--
  @file 项目结项管理
  @description 项目交付完成后的结项流程管理页面，支持正式结项/预结项/强制结项三种类型，
               状态流转: DRAFT → SUBMITTED → APPROVED → ARCHIVED / REJECTED，
               由后端 ClosureAdmissionValidator 校验结项准入条件（回款比例、毛利率等）。
  @module views/execution/closure
-->
<script setup lang="ts">
/**
 * 项目结项管理
 *
 * 类型: FORMAL(正式结项) / PRE_CLOSURE(预结项) / FORCED(强制结项)
 * 状态: DRAFT -> SUBMITTED -> APPROVED -> ARCHIVED / REJECTED
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useI18n } from 'vue-i18n'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pageProjectClosures,
  createProjectClosure,
  changeProjectClosureStatus,
} from '@/api/execution/closure'
import type { ProjectClosureVO, ProjectClosureCreateDTO } from '@/api/execution/closure/types'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

/** 列表加载状态 */
const loading = ref(false)
/** 结项记录列表 */
const list = ref<ProjectClosureVO[]>([])
/** 记录总数（分页用） */
const total = ref(0)
/** 查询条件：关键字 + 类型 + 状态 + 项目 ID */
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  type: '',
  initiationId: undefined as number | undefined,
})

/** 结项类型 → 标签/样式映射 */
const typeMap = {
  FORMAL: { label: t('execution.closure.type.FORMAL'), type: 'primary' as const },
  PRE_CLOSURE: { label: t('execution.closure.type.PRE_CLOSURE'), type: 'warning' as const },
  FORCED: { label: t('execution.closure.type.FORCED'), type: 'danger' as const },
}

/** 结项状态 → 标签/样式映射 */
const statusMap = {
  DRAFT: { label: t('execution.closure.status.DRAFT'), type: 'info' as const },
  SUBMITTED: { label: t('execution.closure.status.SUBMITTED'), type: 'warning' as const },
  APPROVED: { label: t('execution.closure.status.APPROVED'), type: 'success' as const },
  REJECTED: { label: t('execution.closure.status.REJECTED'), type: 'danger' as const },
  ARCHIVED: { label: t('execution.closure.status.ARCHIVED'), type: 'info' as const },
}

/** 分页查询结项列表 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageProjectClosures(query.page, query.size, {
      keyword: query.keyword,
      status: query.status,
      type: query.type,
      initiationId: query.initiationId,
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 重置查询条件并回到首页刷新 */
function handleReset() {
  query.keyword = ''
  query.status = ''
  query.type = ''
  query.initiationId = undefined
  query.page = 1
  fetchList()
}

/** 提交按钮 loading 状态，防止重复提交 */
const submitting = ref(false)
/** 新建结项弹窗可见性 */
const dialogVisible = ref(false)
/** 表单引用（用于校验） */
const formRef = ref<FormInstance>()
/** 新建结项表单数据 */
const form = reactive<Partial<ProjectClosureCreateDTO>>({
  closureCode: '',
  initiationId: 0,
  type: 'FORMAL',
  reason: '',
  summary: '',
  lessonsLearned: '',
  warrantyEndDate: '',
})

/** 表单校验规则 */
const formRules = {
  closureCode: [{ required: true, message: t('execution.closure.rules.closureCodeRequired'), trigger: 'blur' }],
  initiationId: [{ required: true, message: t('execution.closure.rules.initiationIdRequired'), trigger: 'blur' }],
  type: [{ required: true, message: t('execution.closure.rules.typeRequired'), trigger: 'change' }],
}

/** 打开新建弹窗并重置表单 */
function openCreate() {
  Object.assign(form, {
    closureCode: '',
    initiationId: 0,
    type: 'FORMAL',
    reason: '',
    summary: '',
    lessonsLearned: '',
    warrantyEndDate: '',
  })
  dialogVisible.value = true
}

/** 提交新建结项单，校验通过后创建并刷新列表 */
async function submitForm() {
  try {
    submitting.value = true
    await formRef.value?.validate()
    await createProjectClosure(form as ProjectClosureCreateDTO)
    ElMessage.success(t('execution.closure.messages.created'))
    dialogVisible.value = false
    fetchList()
  } catch {
    // 拦截器已弹错，保持弹窗打开
  } finally {
    submitting.value = false
  }
}

/**
 * 变更结项状态（提交/通过/驳回/归档），需二次确认
 * @param row 结项记录
 * @param target 目标状态
 */
async function handleStatus(row: ProjectClosureVO, target: string) {
  const targetText = (statusMap as any)[target]?.label || target
  try {
    await ElMessageBox.confirm(t('execution.closure.messages.confirmStatusChange', { target: targetText }), t('common.tip'), { type: 'warning' })
    await changeProjectClosureStatus({ id: row.id, targetStatus: target })
    ElMessage.success(t('execution.closure.messages.statusUpdated'))
    fetchList()
  } catch { /* 取消 */ }
}

/** 页面挂载时加载列表 */
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
    <!-- 搜索栏 -->
    <template #search>
      <el-form-item :label="$t('execution.closure.search.keyword')"><el-input v-model="query.keyword" :placeholder="$t('execution.closure.search.keywordPlaceholder')" clearable @keyup.enter="query.page = 1; fetchList()" /></el-form-item>
      <el-form-item :label="$t('execution.closure.search.type')">
        <el-select v-model="query.type" :placeholder="$t('common.all')" clearable style="width: 130px">
          <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('execution.closure.search.status')">
        <el-select v-model="query.status" :placeholder="$t('common.all')" clearable style="width: 130px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
    </template>

    <!-- 工具栏 -->
    <template #toolbar>
      <el-button v-permission="[PC.CLOSURE_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        {{ $t('execution.closure.buttons.create') }}
      </el-button>
    </template>

    <!-- 结项列表表格 -->
    <template #table="scope">
      <vxe-table :data="list" :loading="loading" border stripe :height="scope.tableProps.height" :scroll-y="scope.tableProps.scrollY">
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="closureCode" :title="$t('execution.closure.columns.closureCode')" width="160" />
        <vxe-column field="initiationName" :title="$t('execution.closure.columns.initiationName')" min-width="200" show-overflow />
        <vxe-column field="type" :title="$t('execution.closure.columns.type')" width="100">
          <template #default="{ row }"><StatusTag :value="row.type" :map="typeMap" /></template>
        </vxe-column>
        <vxe-column field="status" :title="$t('execution.closure.columns.status')" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column field="paymentRatio" :title="$t('execution.closure.columns.paymentRatio')" width="110" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `${(Number(cellValue) * 100).toFixed(0)}%` : '-'" />
        <vxe-column field="grossMargin" :title="$t('execution.closure.columns.grossMargin')" width="100" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `${(Number(cellValue) * 100).toFixed(1)}%` : '-'" />
        <vxe-column field="applicantName" :title="$t('execution.closure.columns.applicantName')" width="100" />
        <vxe-column field="approverName" :title="$t('execution.closure.columns.approverName')" width="100" />
        <vxe-column field="warrantyEndDate" :title="$t('execution.closure.columns.warrantyEndDate')" width="110" />
        <vxe-column field="createdAt" :title="$t('execution.closure.columns.createdAt')" width="170" />
        <vxe-column :title="$t('execution.closure.columns.action')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" v-permission="[PC.CLOSURE_STATUS]" link type="warning" size="small" @click="handleStatus(row, 'SUBMITTED')">{{ $t('common.submit') }}</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.CLOSURE_STATUS]" link type="success" size="small" @click="handleStatus(row, 'APPROVED')">{{ $t('execution.closure.buttons.approve') }}</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.CLOSURE_STATUS]" link type="danger" size="small" @click="handleStatus(row, 'REJECTED')">{{ $t('execution.closure.buttons.reject') }}</el-button>
            <el-button v-if="row.status === 'APPROVED'" v-permission="[PC.CLOSURE_STATUS]" link type="info" size="small" @click="handleStatus(row, 'ARCHIVED')">{{ $t('execution.closure.buttons.archive') }}</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <!-- 新建结项弹窗 -->
    <el-dialog v-model="dialogVisible" :title="$t('execution.closure.dialog.createTitle')" width="640px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item :label="$t('execution.closure.dialog.closureCode')" prop="closureCode"><el-input v-model="form.closureCode" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('execution.closure.dialog.initiationId')" prop="initiationId"><el-input-number v-model="form.initiationId" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item :label="$t('execution.closure.dialog.closureType')" prop="type">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('execution.closure.dialog.reason')"><el-input v-model="form.reason" type="textarea" :rows="2" /></el-form-item>
        <el-form-item :label="$t('execution.closure.dialog.summary')"><el-input v-model="form.summary" type="textarea" :rows="3" /></el-form-item>
        <el-form-item :label="$t('execution.closure.dialog.lessonsLearned')"><el-input v-model="form.lessonsLearned" type="textarea" :rows="3" /></el-form-item>
        <el-form-item :label="$t('execution.closure.dialog.warrantyEndDate')">
          <el-date-picker v-model="form.warrantyEndDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">{{ $t('common.ok') }}</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
