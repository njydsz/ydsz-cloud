<!--
  @file 质保期管理
  @description 售后质保期管理页面，支持质保期创建、提前终止、到期/过期扫描提醒。
  @module views/aftersales/warranty
-->
<script setup lang="ts">
/**
 * 质保期管理 (P7)
 *
 * 状态: ACTIVE / EXPIRING_SOON / EXPIRED / TERMINATED
 * 操作: 创建 / 终止 / 扫描即将到期 / 扫描已过期
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pageWarranties,
  createWarranty,
  terminateWarranty,
  scanExpiringWarranty,
  scanOverdueWarranty,
} from '@/api/execution/aftersales/warranty'
import type { WarrantyVO, WarrantyCreateDTO } from '@/api/execution/aftersales/types'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

/** 列表加载状态 */
const loading = ref(false)
/** 质保期列表数据 */
const list = ref<WarrantyVO[]>([])
/** 列表总条数（用于分页） */
const total = ref(0)
/** 列表查询条件 */
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  initiationId: undefined as number | undefined,
})

const statusMap = computed(() => ({
  ACTIVE: { label: t('aftersales.warranty.status.ACTIVE'), type: 'success' as const },
  EXPIRING_SOON: { label: t('aftersales.warranty.status.EXPIRING_SOON'), type: 'warning' as const },
  EXPIRED: { label: t('aftersales.warranty.status.EXPIRED'), type: 'info' as const },
  TERMINATED: { label: t('aftersales.warranty.status.TERMINATED'), type: 'danger' as const },
}))

/** 拉取质保期分页列表 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageWarranties({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      initiationId: query.initiationId,
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 重置查询条件并重新加载列表 */
function handleReset() {
  query.keyword = ''
  query.status = ''
  query.initiationId = undefined
  query.page = 1
  fetchList()
}

/** 是否处于空态: 非加载中且列表无数据 */
const isEmpty = computed(() => !loading.value && list.value.length === 0)

/** 选中的质保期行 (用于批量操作) */
const selectedRows = ref<WarrantyVO[]>([])

/** 表格勾选行变更回调，同步本地选中列表 */
function onSelectionChange({ rows }: { rows: WarrantyVO[] }) {
  selectedRows.value = rows
}

/** 新增质保期弹窗显隐 */
const dialogVisible = ref(false)
/** 新增质保期表单引用 */
const formRef = ref<any>()
/** 新增质保期表单数据 */
const form = reactive<Partial<WarrantyCreateDTO>>({
  initiationId: 0,
  durationMonths: 12,
  noticeDays: 30,
})

const formRules = {
  initiationId: [{ required: true, message: t('aftersales.warranty.rules.initiationIdRequired'), trigger: 'blur' }],
  durationMonths: [{ required: true, message: t('aftersales.warranty.rules.durationMonthsRequired'), trigger: 'blur' }],
}

/** 打开新增质保期弹窗，重置表单为默认值 */
function openCreate() {
  Object.assign(form, {
    initiationId: 0,
    durationMonths: 12,
    noticeDays: 30,
    startDate: undefined,
    contactName: '',
    contactPhone: '',
    description: '',
  })
  dialogVisible.value = true
}

/** 提交新增质保期表单，校验通过后调用创建接口 */
async function submitForm() {
  await formRef.value?.validate()
  await createWarranty(form as WarrantyCreateDTO)
  ElMessage.success(t('aftersales.warranty.messages.created'))
  dialogVisible.value = false
  fetchList()
}

/**
 * 提前终止指定质保期，需输入终止原因
 * @param row 当前行质保期数据
 */
async function handleTerminate(row: WarrantyVO) {
  try {
    const { value } = await ElMessageBox.prompt(t('aftersales.warranty.messages.terminatePrompt'), t('aftersales.warranty.messages.terminateTitle'), {
      inputValidator: (v) => !!v || t('aftersales.warranty.messages.reasonRequired'),
    })
    await terminateWarranty({ id: row.id, reason: value })
    ElMessage.success(t('aftersales.warranty.messages.terminated'))
    fetchList()
  } catch { /* 取消 */ }
}

/**
 * 触发质保期扫描任务并刷新列表
 * @param type 扫描类型：expiring-即将到期(30天内) / overdue-已过期
 */
async function handleScan(type: 'expiring' | 'overdue') {
  if (type === 'expiring') {
    const n = await scanExpiringWarranty(30)
    ElMessage.success(t('aftersales.warranty.messages.scannedExpiring', { count: n }))
  } else {
    const n = await scanOverdueWarranty()
    ElMessage.success(t('aftersales.warranty.messages.scannedOverdue', { count: n }))
  }
  fetchList()
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
      <el-form-item :label="t('aftersales.warranty.search.keyword')"><el-input v-model="query.keyword" :placeholder="t('aftersales.warranty.search.keywordPlaceholder')" clearable /></el-form-item>
      <el-form-item :label="t('aftersales.warranty.search.status')">
        <el-select v-model="query.status" :placeholder="t('common.all')" clearable style="width: 130px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('aftersales.warranty.search.initiationId')"><el-input-number v-model="query.initiationId" :min="0" :controls="false" /></el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.AFTERSALES_WARRANTY_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        {{ t('aftersales.warranty.buttons.create') }}
      </el-button>
      <el-button v-permission="[PC.AFTERSALES_WARRANTY_SCAN]" type="warning" :icon="'Bell'" @click="handleScan('expiring')">
        {{ t('aftersales.warranty.buttons.scanExpiring') }}
      </el-button>
      <el-button v-permission="[PC.AFTERSALES_WARRANTY_SCAN]" type="danger" :icon="'Warning'" @click="handleScan('overdue')">
        {{ t('aftersales.warranty.buttons.scanOverdue') }}
      </el-button>
    </template>

    <template #table>
      <EmptyState
        v-if="isEmpty"
        preset="search"
        :title="query.keyword || query.status || query.initiationId ? t('aftersales.warranty.empty.searchTitle') : t('aftersales.warranty.empty.listTitle')"
        :description="query.keyword || query.status || query.initiationId ? t('aftersales.warranty.empty.searchDesc') : t('aftersales.warranty.empty.listDesc')"
        :action-text="t('aftersales.warranty.empty.actionCreate')"
        @action="openCreate"
      />
      <vxe-table v-else :data="list" :loading="loading" border stripe @checkbox-change="onSelectionChange" @checkbox-all="onSelectionChange">
        <vxe-column type="checkbox" width="50" />
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="warrantyCode" :title="t('aftersales.warranty.columns.warrantyCode')" width="200" />
        <vxe-column field="initiationName" :title="t('aftersales.warranty.columns.initiationName')" min-width="200" show-overflow />
        <vxe-column field="startDate" :title="t('aftersales.warranty.columns.startDate')" width="110" />
        <vxe-column field="endDate" :title="t('aftersales.warranty.columns.endDate')" width="110" />
        <vxe-column field="durationMonths" :title="t('aftersales.warranty.columns.durationMonths')" width="90" align="center" />
        <vxe-column field="noticeDays" :title="t('aftersales.warranty.columns.noticeDays')" width="100" align="center" />
        <vxe-column field="contactName" :title="t('aftersales.warranty.columns.contactName')" width="100" />
        <vxe-column field="contactPhone" :title="t('aftersales.warranty.columns.contactPhone')" width="130" />
        <vxe-column field="status" :title="t('aftersales.warranty.columns.status')" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column field="terminatedAt" :title="t('aftersales.warranty.columns.terminatedAt')" width="170" />
        <vxe-column field="terminationReason" :title="t('aftersales.warranty.columns.terminationReason')" min-width="180" show-overflow />
        <vxe-column :title="t('aftersales.warranty.columns.action')" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'ACTIVE' || row.status === 'EXPIRING_SOON'"
              v-permission="[PC.AFTERSALES_WARRANTY_TERMINATE]"
              link
              type="danger"
              size="small"
              @click="handleTerminate(row)"
            >
              {{ t('aftersales.warranty.buttons.terminate') }}
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <el-dialog v-model="dialogVisible" :title="t('aftersales.warranty.dialog.createTitle')" width="560px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item :label="t('aftersales.warranty.form.initiationId')" prop="initiationId">
          <el-input-number v-model="form.initiationId" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('aftersales.warranty.form.durationMonths')" prop="durationMonths">
          <el-input-number v-model="form.durationMonths" :min="1" :max="120" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('aftersales.warranty.form.noticeDays')">
          <el-input-number v-model="form.noticeDays" :min="1" :max="180" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('aftersales.warranty.form.startDate')">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('aftersales.warranty.form.contactName')"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item :label="t('aftersales.warranty.form.contactPhone')"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item :label="t('aftersales.warranty.form.description')">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitForm">{{ t('common.ok') }}</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
