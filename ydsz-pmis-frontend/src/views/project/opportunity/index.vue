<script setup lang="ts">
/**
 * 商机管理页面
 *
 * 提供商机的查询、新增、编辑、状态流转、赢率评估、转立项等操作。
 * 状态机: FOLLOWING -> QUOTED -> NEGOTIATING -> WON -> CONVERTED / LOST
 */
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pageOpportunities,
  createOpportunity,
  updateOpportunity,
  changeOpportunityStatus,
  deleteOpportunity,
  evaluateWinRate,
  convertToInitiation,
} from '@/api/project/opportunity'
import type { OpportunityVO, OpportunityCreateDTO, OpportunityUpdateDTO } from '@/api/project/opportunity/types'
import { PC } from '@/constants/permissionCodes'
import { useFormDraft } from '@/composables/useFormDraft'
import { useUserStore } from '@/store/modules/user'

const { t } = useI18n()

const loading = ref(false)
const submitting = ref(false)
const list = ref<OpportunityVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  level: '',
  ownerId: undefined as number | undefined,
})

const statusMap = computed(() => ({
  FOLLOWING: { label: t('project.opportunity.status.FOLLOWING'), type: 'info' as const },
  QUOTED: { label: t('project.opportunity.status.QUOTED'), type: 'warning' as const },
  NEGOTIATING: { label: t('project.opportunity.status.NEGOTIATING'), type: 'primary' as const },
  WON: { label: t('project.opportunity.status.WON'), type: 'success' as const },
  CONVERTED: { label: t('project.opportunity.status.CONVERTED'), type: 'success' as const },
  LOST: { label: t('project.opportunity.status.LOST'), type: 'danger' as const },
  INVALID: { label: t('project.opportunity.status.INVALID'), type: 'info' as const },
}))

const levelMap = computed(() => ({
  A: { label: t('project.opportunity.level.A'), type: 'danger' as const },
  B: { label: t('project.opportunity.level.B'), type: 'warning' as const },
  C: { label: t('project.opportunity.level.C'), type: 'info' as const },
  D: { label: t('project.opportunity.level.D'), type: 'info' as const },
}))

async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageOpportunities(query.page, query.size, {
      keyword: query.keyword,
      status: query.status,
      level: query.level,
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
  query.level = ''
  query.ownerId = undefined
  query.page = 1
  fetchList()
}

// 表单弹窗
const dialogVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formRef = ref<FormInstance>()
const form = reactive<Partial<OpportunityVO> & { id?: number }>({
  opportunityCode: '',
  opportunityName: '',
  customerId: 0,
  customerName: '',
  ownerId: 0,
  ownerName: '',
  level: 'C',
  estimatedAmount: undefined,
})

const formRules = computed(() => ({
  opportunityCode: [{ required: true, message: t('project.opportunity.rules.opportunityCodeRequired'), trigger: 'blur' }],
  opportunityName: [{ required: true, message: t('project.opportunity.rules.opportunityNameRequired'), trigger: 'blur' }],
  customerId: [{ required: true, message: t('project.opportunity.rules.customerIdRequired'), trigger: 'blur' }],
  ownerId: [{ required: true, message: t('project.opportunity.rules.ownerIdRequired'), trigger: 'blur' }],
}))

// ===== 表单草稿 =====
const userStore = useUserStore()
const { hasDraft, lastSavedAt, restore, clear: clearDraft } = useFormDraft(form, {
  key: 'opportunity-create',
  debounce: 3000,
  userId: userStore.userInfo?.id,
})

const draftTimeText = computed(() => {
  if (!lastSavedAt.value) return ''
  return lastSavedAt.value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
})

/** 打开新增商机弹窗，重置表单为初始值；若检测到草稿则提示恢复 */
function openCreate() {
  if (hasDraft.value) {
    ElMessageBox.confirm(t('project.opportunity.messages.confirmRestoreDraft'), t('common.confirm'), { type: 'info' })
      .then(() => {
        restore()
        ElMessage.success(t('project.opportunity.messages.draftRestored'))
        formMode.value = 'create'
        dialogVisible.value = true
      })
      .catch(() => {
        clearDraft()
        formMode.value = 'create'
        Object.assign(form, {
          id: undefined,
          opportunityCode: '',
          opportunityName: '',
          customerId: 0,
          customerName: '',
          ownerId: 0,
          ownerName: '',
          level: 'C',
          estimatedAmount: undefined,
          status: 'FOLLOWING',
          source: '',
          industry: '',
        })
        dialogVisible.value = true
      })
    return
  }
  formMode.value = 'create'
  Object.assign(form, {
    id: undefined,
    opportunityCode: '',
    opportunityName: '',
    customerId: 0,
    customerName: '',
    ownerId: 0,
    ownerName: '',
    level: 'C',
    estimatedAmount: undefined,
    status: 'FOLLOWING',
    source: '',
    industry: '',
  })
  dialogVisible.value = true
}

async function openEdit(row: OpportunityVO) {
  clearDraft()
  formMode.value = 'edit'
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (formMode.value === 'create') {
      const dto: OpportunityCreateDTO = {
        opportunityCode: form.opportunityCode!,
        opportunityName: form.opportunityName!,
        customerId: form.customerId!,
        customerName: form.customerName,
        ownerId: form.ownerId!,
        ownerName: form.ownerName,
        level: form.level,
        estimatedAmount: form.estimatedAmount,
        source: form.source,
        industry: form.industry,
      }
      await createOpportunity(dto)
      clearDraft()
      ElMessage.success(t('project.opportunity.messages.createSuccess'))
    } else if (form.id) {
      const dto: OpportunityUpdateDTO = {
        id: form.id,
        opportunityName: form.opportunityName,
        level: form.level,
        industry: form.industry,
        estimatedAmount: form.estimatedAmount,
        winRate: form.winRate,
        expectedSignDate: form.expectedSignDate,
        expectedStartDate: form.expectedStartDate,
        expectedEndDate: form.expectedEndDate,
        competitor: form.competitor,
        remark: form.remark,
        tags: form.tags,
      }
      await updateOpportunity(dto)
      ElMessage.success(t('project.opportunity.messages.updateSuccess'))
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: OpportunityVO) {
  try {
    await ElMessageBox.confirm(t('project.opportunity.messages.confirmDelete', { name: row.opportunityName }), t('common.confirm'), { type: 'warning' })
    await deleteOpportunity(row.id)
    ElMessage.success(t('project.opportunity.messages.deleteSuccess'))
    fetchList()
  } catch { /* 取消 */ }
}

/**
 * 变更商机状态（二次确认），状态机见文件头
 * @param row 选中的商机行数据
 * @param target 目标状态编码
 */
async function handleChangeStatus(row: OpportunityVO, target: string) {
  const targetText = statusMap.value[target as keyof typeof statusMap.value]?.label || target
  try {
    await ElMessageBox.confirm(t('project.opportunity.messages.confirmStatusChange', { target: targetText }), t('common.confirm'), { type: 'warning' })
    await changeOpportunityStatus({ id: row.id, targetStatus: target })
    ElMessage.success(t('project.opportunity.messages.statusUpdated'))
    fetchList()
  } catch { /* 取消 */ }
}

async function handleEvaluate(row: OpportunityVO) {
  try {
    const { data } = await evaluateWinRate(row.id, (row as any).customerCredit, false)
    ElMessage.success(t('project.opportunity.messages.evaluateResult', { rate: (data * 100).toFixed(1) }))
    fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || t('project.opportunity.messages.evaluateFailed'))
  }
}

async function handleConvert(row: OpportunityVO) {
  try {
    await ElMessageBox.confirm(
      t('project.opportunity.messages.confirmConvert', { name: row.opportunityName }),
      t('project.opportunity.dialog.convertTitle'),
      { type: 'info' },
    )
    const { data } = await convertToInitiation(row.id)
    ElMessage.success(t('project.opportunity.messages.convertSuccess', { id: data }))
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
      <el-form-item :label="$t('project.opportunity.search.keyword')">
        <el-input v-model="query.keyword" :placeholder="$t('project.opportunity.search.keywordPlaceholder')" clearable @keyup.enter="query.page = 1; fetchList()" />
      </el-form-item>
      <el-form-item :label="$t('project.opportunity.search.status')">
        <el-select v-model="query.status" :placeholder="$t('common.all')" clearable style="width: 140px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('project.opportunity.search.level')">
        <el-select v-model="query.level" :placeholder="$t('common.all')" clearable style="width: 120px">
          <el-option v-for="(v, k) in levelMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.PROJECT_OPPORTUNITY_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        {{ $t('project.opportunity.buttons.create') }}
      </el-button>
    </template>

    <template #table="scope">
      <vxe-table :data="list" :loading="loading" border stripe :height="scope.tableProps.height" :scroll-y="scope.tableProps.scrollY">
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="opportunityCode" :title="$t('project.opportunity.columns.opportunityCode')" width="160" />
        <vxe-column field="opportunityName" :title="$t('project.opportunity.columns.opportunityName')" min-width="200" show-overflow />
        <vxe-column field="customerName" :title="$t('project.opportunity.columns.customerName')" width="160" show-overflow />
        <vxe-column field="ownerName" :title="$t('project.opportunity.columns.ownerName')" width="100" />
        <vxe-column field="level" :title="$t('project.opportunity.columns.level')" width="80" align="center">
          <template #default="{ row }">
            <StatusTag :value="row.level" :map="levelMap" fallback-type="info" />
          </template>
        </vxe-column>
        <vxe-column field="estimatedAmount" :title="$t('project.opportunity.columns.estimatedAmount')" width="120" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="winRate" :title="$t('project.opportunity.columns.winRate')" width="80" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `${(Number(cellValue) * 100).toFixed(0)}%` : '-'" />
        <vxe-column field="expectedSignDate" :title="$t('project.opportunity.columns.expectedSignDate')" width="110" />
        <vxe-column field="status" :title="$t('project.opportunity.columns.status')" width="110">
          <template #default="{ row }">
            <StatusTag :value="row.status" :map="statusMap" />
          </template>
        </vxe-column>
        <vxe-column :title="$t('project.opportunity.columns.action')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="[PC.PROJECT_OPPORTUNITY_UPDATE]" link type="primary" size="small" @click="openEdit(row)">
              {{ $t('project.opportunity.buttons.edit') }}
            </el-button>
            <el-button v-if="row.status === 'WON'" v-permission="[PC.PROJECT_OPPORTUNITY_CONVERT]" link type="success" size="small" @click="handleConvert(row)">
              {{ $t('project.opportunity.buttons.convert') }}
            </el-button>
            <el-button v-permission="[PC.PROJECT_OPPORTUNITY_EVALUATE]" link type="primary" size="small" @click="handleEvaluate(row)">
              {{ $t('project.opportunity.buttons.evaluate') }}
            </el-button>
            <el-button v-if="row.status === 'FOLLOWING'" v-permission="[PC.PROJECT_OPPORTUNITY_UPDATE]" link type="warning" size="small" @click="handleChangeStatus(row, 'QUOTED')">
              {{ $t('project.opportunity.buttons.toQuoted') }}
            </el-button>
            <el-button v-if="row.status === 'QUOTED'" v-permission="[PC.PROJECT_OPPORTUNITY_UPDATE]" link type="warning" size="small" @click="handleChangeStatus(row, 'NEGOTIATING')">
              {{ $t('project.opportunity.buttons.toNegotiating') }}
            </el-button>
            <el-button v-if="row.status === 'NEGOTIATING'" v-permission="[PC.PROJECT_OPPORTUNITY_UPDATE]" link type="success" size="small" @click="handleChangeStatus(row, 'WON')">
              {{ $t('project.opportunity.buttons.win') }}
            </el-button>
            <el-button v-if="['FOLLOWING', 'QUOTED', 'NEGOTIATING'].includes(row.status || '')" v-permission="[PC.PROJECT_OPPORTUNITY_UPDATE]" link type="danger" size="small" @click="handleChangeStatus(row, 'LOST')">
              {{ $t('project.opportunity.buttons.lose') }}
            </el-button>
            <el-button v-permission="[PC.PROJECT_OPPORTUNITY_DELETE]" link type="danger" size="small" @click="handleDelete(row)">
              {{ $t('common.delete') }}
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <template #footer>
      <el-dialog v-model="dialogVisible" :title="formMode === 'create' ? $t('project.opportunity.dialog.createTitle') : $t('project.opportunity.dialog.editTitle')" width="720px">
        <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item :label="$t('project.opportunity.form.opportunityCode')" prop="opportunityCode">
                <el-input v-model="form.opportunityCode" :disabled="formMode === 'edit'" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('project.opportunity.form.opportunityName')" prop="opportunityName">
                <el-input v-model="form.opportunityName" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item :label="$t('project.opportunity.form.customerId')" prop="customerId">
                <el-input-number v-model="form.customerId" :min="1" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('project.opportunity.form.customerName')">
                <el-input v-model="form.customerName" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item :label="$t('project.opportunity.form.ownerId')" prop="ownerId">
                <el-input-number v-model="form.ownerId" :min="1" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('project.opportunity.form.ownerName')">
                <el-input v-model="form.ownerName" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item :label="$t('project.opportunity.form.level')">
                <el-select v-model="form.level" style="width: 100%">
                  <el-option v-for="(v, k) in levelMap" :key="k" :label="v.label" :value="k" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('project.opportunity.form.estimatedAmount')">
                <el-input-number v-model="form.estimatedAmount" :min="0" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item :label="$t('project.opportunity.form.source')">
                <el-input v-model="form.source" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('project.opportunity.form.industry')">
                <el-input v-model="form.industry" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item :label="$t('project.opportunity.form.remark')">
            <el-input v-model="form.remark" type="textarea" :rows="2" />
          </el-form-item>
        </el-form>
        <template #footer>
          <span v-if="draftTimeText" style="color: #909399; font-size: 12px; margin-right: auto;">草稿已保存 {{ draftTimeText }}</span>
          <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="submitting" @click="submitForm">{{ $t('common.confirm') }}</el-button>
        </template>
      </el-dialog>
    </template>
  </PageLayout>
</template>
