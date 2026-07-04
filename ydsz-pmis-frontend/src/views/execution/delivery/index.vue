<!--
  @file 交付物管理
  @description 项目交付物全生命周期管理页面，覆盖 CD1~CD5 五个交付阶段，
               支持标准交付物与项目特异交付物的创建、提交、验收、驳回、豁免操作；
               状态流转: PENDING → SUBMITTED → ACCEPTED / REJECTED / WAIVED。
  @module views/execution/delivery
-->
<script setup lang="ts">
/**
 * 交付物管理
 *
 * 阶段: CD1_KICKOFF -> CD2_DESIGN -> CD3_BUILD -> CD4_UAT -> CD5_GO_LIVE
 * 类型: STANDARD(标准交付) / SPECIFIC(项目特异)
 * 状态: PENDING -> SUBMITTED -> ACCEPTED / REJECTED / WAIVED
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pageDeliveryItems,
  createDeliveryItem,
  changeDeliveryItemStatus,
} from '@/api/execution/delivery'
import type { DeliveryItemVO, DeliveryItemCreateDTO } from '@/api/execution/delivery/types'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

/** 列表加载状态 */
const loading = ref(false)
/** 交付物记录列表 */
const list = ref<DeliveryItemVO[]>([])
/** 记录总数（分页用） */
const total = ref(0)
/** 查询条件：关键字 + 状态 + 项目 ID + 阶段 */
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  initiationId: undefined as number | undefined,
  stage: '',
})

/** 交付阶段 → 标签/样式映射（CD1~CD5） */
const stageMap = {
  CD1_KICKOFF: { label: t('execution.delivery.stage.CD1_KICKOFF'), type: 'info' as const },
  CD2_DESIGN: { label: t('execution.delivery.stage.CD2_DESIGN'), type: 'primary' as const },
  CD3_BUILD: { label: t('execution.delivery.stage.CD3_BUILD'), type: 'primary' as const },
  CD4_UAT: { label: t('execution.delivery.stage.CD4_UAT'), type: 'warning' as const },
  CD5_GO_LIVE: { label: t('execution.delivery.stage.CD5_GO_LIVE'), type: 'success' as const },
}

/** 交付物类型 → 标签/样式映射 */
const typeMap = {
  STANDARD: { label: t('execution.delivery.type.STANDARD'), type: 'info' as const },
  SPECIFIC: { label: t('execution.delivery.type.SPECIFIC'), type: 'primary' as const },
}

/** 交付物状态 → 标签/样式映射 */
const statusMap = {
  PENDING: { label: t('execution.delivery.status.PENDING'), type: 'info' as const },
  SUBMITTED: { label: t('execution.delivery.status.SUBMITTED'), type: 'warning' as const },
  ACCEPTED: { label: t('execution.delivery.status.ACCEPTED'), type: 'success' as const },
  REJECTED: { label: t('execution.delivery.status.REJECTED'), type: 'danger' as const },
  WAIVED: { label: t('execution.delivery.status.WAIVED'), type: 'info' as const },
}

/** 分页查询交付物列表 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageDeliveryItems(query.page, query.size, {
      keyword: query.keyword,
      status: query.status,
      initiationId: query.initiationId,
      stage: query.stage,
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
  query.initiationId = undefined
  query.stage = ''
  query.page = 1
  fetchList()
}

/** 提交按钮 loading 状态，防止重复提交 */
const submitting = ref(false)
/** 新增交付物弹窗可见性 */
const dialogVisible = ref(false)
/** 表单引用（用于校验） */
const formRef = ref<any>()
/** 新增交付物表单数据 */
const form = reactive<Partial<DeliveryItemCreateDTO>>({
  initiationId: 0,
  stage: 'CD2_DESIGN',
  type: 'SPECIFIC',
  name: '',
  level: 'NORMAL',
})

/** 表单校验规则 */
const formRules = {
  initiationId: [{ required: true, message: t('execution.delivery.rules.initiationIdRequired'), trigger: 'blur' }],
  stage: [{ required: true, message: t('execution.delivery.rules.stageRequired'), trigger: 'change' }],
  name: [{ required: true, message: t('execution.delivery.rules.nameRequired'), trigger: 'blur' }],
}

/** 打开新增弹窗并重置表单为默认值 */
function openCreate() {
  Object.assign(form, {
    initiationId: 0,
    stage: 'CD2_DESIGN',
    type: 'SPECIFIC',
    name: '',
    level: 'NORMAL',
    description: '',
    ownerId: undefined,
  })
  dialogVisible.value = true
}

/** 提交新建交付物，校验通过后创建并刷新列表 */
async function submitForm() {
  try {
    submitting.value = true
    await formRef.value?.validate()
    await createDeliveryItem(form as DeliveryItemCreateDTO)
    ElMessage.success(t('execution.delivery.messages.created'))
    dialogVisible.value = false
    fetchList()
  } catch {
    // 拦截器已弹错，保持弹窗打开
  } finally {
    submitting.value = false
  }
}

/**
 * 变更交付物状态（提交/验收/驳回/豁免），驳回需填写原因
 * @param row 交付物记录
 * @param target 目标状态
 */
async function handleStatus(row: DeliveryItemVO, target: string) {
  try {
    let reason: string | undefined
    if (target === 'REJECTED') {
      const { value } = await ElMessageBox.prompt(t('execution.delivery.messages.rejectPrompt'), t('execution.delivery.messages.rejectTitle'), { inputValidator: (v) => !!v || t('execution.delivery.messages.rejectReasonRequired') })
      reason = value
    }
    await changeDeliveryItemStatus({ id: row.id, targetStatus: target, reason })
    ElMessage.success(t('execution.delivery.messages.statusUpdated'))
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
      <el-form-item :label="$t('execution.delivery.search.keyword')"><el-input v-model="query.keyword" :placeholder="$t('execution.delivery.search.keywordPlaceholder')" clearable /></el-form-item>
      <el-form-item :label="$t('execution.delivery.search.stage')">
        <el-select v-model="query.stage" :placeholder="$t('common.all')" clearable style="width: 130px">
          <el-option v-for="(v, k) in stageMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('execution.delivery.search.status')">
        <el-select v-model="query.status" :placeholder="$t('common.all')" clearable style="width: 130px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('execution.delivery.search.initiationId')"><el-input-number v-model="query.initiationId" :min="0" :controls="false" /></el-form-item>
    </template>

    <!-- 工具栏 -->
    <template #toolbar>
      <el-button v-permission="[PC.EXECUTION_DELIVERY_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        {{ $t('execution.delivery.buttons.create') }}
      </el-button>
    </template>

    <!-- 交付物列表表格 -->
    <template #table="scope">
      <vxe-table :data="list" :loading="loading" border stripe :height="scope.tableProps.height" :scroll-y="scope.tableProps.scrollY">
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="name" :title="$t('execution.delivery.columns.name')" min-width="200" show-overflow />
        <vxe-column field="initiationName" :title="$t('execution.delivery.columns.initiationName')" width="160" show-overflow />
        <vxe-column field="stage" :title="$t('execution.delivery.columns.stage')" width="100">
          <template #default="{ row }"><StatusTag :value="row.stage" :map="stageMap" /></template>
        </vxe-column>
        <vxe-column field="type" :title="$t('execution.delivery.columns.type')" width="100">
          <template #default="{ row }"><StatusTag :value="row.type" :map="typeMap" /></template>
        </vxe-column>
        <vxe-column field="level" :title="$t('execution.delivery.columns.level')" width="80" align="center" />
        <vxe-column field="ownerName" :title="$t('execution.delivery.columns.ownerName')" width="100" />
        <vxe-column field="submittedAt" :title="$t('execution.delivery.columns.submittedAt')" width="170" />
        <vxe-column field="acceptedAt" :title="$t('execution.delivery.columns.acceptedAt')" width="170" />
        <vxe-column field="status" :title="$t('execution.delivery.columns.status')" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column :title="$t('execution.delivery.columns.action')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" v-permission="[PC.EXECUTION_DELIVERY_REVIEW]" link type="warning" size="small" @click="handleStatus(row, 'SUBMITTED')">{{ $t('execution.delivery.buttons.submit') }}</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.EXECUTION_DELIVERY_REVIEW]" link type="success" size="small" @click="handleStatus(row, 'ACCEPTED')">{{ $t('execution.delivery.buttons.accept') }}</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.EXECUTION_DELIVERY_REVIEW]" link type="danger" size="small" @click="handleStatus(row, 'REJECTED')">{{ $t('execution.delivery.buttons.reject') }}</el-button>
            <el-button v-if="['PENDING', 'REJECTED'].includes(row.status || '')" v-permission="[PC.EXECUTION_DELIVERY_REVIEW]" link type="info" size="small" @click="handleStatus(row, 'WAIVED')">{{ $t('execution.delivery.buttons.waive') }}</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <!-- 新增交付物弹窗 -->
    <el-dialog v-model="dialogVisible" :title="$t('execution.delivery.dialog.createTitle')" width="520px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item :label="$t('execution.delivery.dialog.initiationId')" prop="initiationId"><el-input-number v-model="form.initiationId" :min="1" :controls="false" style="width: 100%" /></el-form-item>
        <el-form-item :label="$t('execution.delivery.dialog.stage')" prop="stage">
          <el-select v-model="form.stage" style="width: 100%">
            <el-option v-for="(v, k) in stageMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('execution.delivery.dialog.type')">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('execution.delivery.dialog.name')" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item :label="$t('execution.delivery.dialog.level')">
          <el-select v-model="form.level" style="width: 100%">
            <el-option :label="$t('execution.delivery.level.NORMAL')" value="NORMAL" />
            <el-option :label="$t('execution.delivery.level.IMPORTANT')" value="IMPORTANT" />
            <el-option :label="$t('execution.delivery.level.CRITICAL')" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('execution.delivery.dialog.ownerId')">
          <el-input-number v-model="form.ownerId" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('execution.delivery.dialog.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">{{ $t('common.ok') }}</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
