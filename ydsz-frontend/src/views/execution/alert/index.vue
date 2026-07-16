<!--
  @file 预警中心
  @description 项目执行过程中的预算/风险/EVM/SLA/Bench 等多维预警中心页面，
               支持预警提交、立即分发、失败重试、取消及等级聚合统计；
               触达规则: 黄色 → PM + PMO / 红色 → PMO + GM + CFO。
  @module views/execution/alert
-->
<script setup lang="ts">
/**
 * 预警中心 (P5)
 *
 * 黄 → PM + PMO / 红 → PMO + GM + CFO
 * 支持: 列表查询 / 提交 / 立即分发 / 重试 / 取消 / 等级聚合
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import VirtualTable from '@/components/common/VirtualTable.vue'
import type { ColumnConfig } from '@/components/common/VirtualTable.vue'
import {
  listAlerts,
  submitAlert,
  dispatchAlertNow,
  retryFailedAlerts,
  cancelAlert,
  aggregateAlerts,
  resolveAlertRoles,
} from '@/api/alert'
import type {
  AlertDispatchVO,
  AlertDispatchDTO,
  AlertAggregateVO,
  AlertResolveRolesVO,
} from '@/api/alert/types'
import { handleError, confirmAction, showSuccess } from '@/utils/error'

const { t } = useI18n()

/** 列表加载状态 */
const loading = ref(false)
/** 预警分发记录列表 */
const list = ref<AlertDispatchVO[]>([])
/** 等级聚合统计（按 alertType + alertLevel 维度） */
const aggregate = ref<AlertAggregateVO[]>([])
/** 查询条件：预警等级 + 分发状态 */
const query = reactive({
  level: '' as '' | 'YELLOW' | 'RED' | 'NORMAL',
  status: '' as '' | 'PENDING' | 'SENT' | 'FAILED' | 'CANCELLED',
})

/** 预警等级 → 标签/样式映射 */
const levelMap = {
  YELLOW: { label: t('execution.alert.level.YELLOW'), type: 'warning' as const },
  RED: { label: t('execution.alert.level.RED'), type: 'danger' as const },
  NORMAL: { label: t('execution.alert.level.NORMAL'), type: 'info' as const },
}

/** 分发状态 → 标签/样式映射 */
const statusMap = {
  PENDING: { label: t('execution.alert.status.PENDING'), type: 'warning' as const },
  SENT: { label: t('execution.alert.status.SENT'), type: 'success' as const },
  FAILED: { label: t('execution.alert.status.FAILED'), type: 'danger' as const },
  CANCELLED: { label: t('execution.alert.status.CANCELLED'), type: 'info' as const },
}

/** 预警业务类型 → 中文名映射 */
const typeMap: Record<string, string> = {
  BUDGET: t('execution.alert.type.BUDGET'),
  RISK: t('execution.alert.type.RISK'),
  EVM: 'EVM',
  SLA: 'SLA',
  BENCH: 'Bench',
  UTILIZATION: t('execution.alert.type.UTILIZATION'),
  QUALITY: t('execution.alert.type.QUALITY'),
  OTHER: t('execution.alert.type.OTHER'),
}

/** 预警分发列表列配置 */
const alertColumns: ColumnConfig[] = [
  { field: 'alertCode', title: t('execution.alert.columns.alertCode'), width: 220 },
  { field: 'alertType', title: t('execution.alert.columns.alertType'), width: 100, slot: true },
  { field: 'alertLevel', title: t('execution.alert.columns.alertLevel'), width: 80, slot: true },
  { field: 'title', title: t('execution.alert.columns.title'), width: 220 },
  { field: 'targetRole', title: t('execution.alert.columns.targetRole'), width: 160 },
  { field: 'pushChannels', title: t('execution.alert.columns.pushChannels'), width: 120 },
  { field: 'status', title: t('execution.alert.columns.status'), width: 100, slot: true },
  { field: 'retryCount', title: t('execution.alert.columns.retryCount'), width: 60 },
  { field: 'dispatchedAt', title: t('execution.alert.columns.dispatchedAt'), width: 160 },
  { field: 'failReason', title: t('execution.alert.columns.failReason'), width: 180 },
  { field: 'actions', title: t('execution.alert.columns.action'), width: 220, fixed: 'right', slot: true },
]

/** 拉取预警分发列表（按 level/status 过滤） */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await listAlerts({
      level: query.level || undefined,
      status: query.status || undefined,
    })
    list.value = data
  } finally {
    loading.value = false
  }
}

/** 拉取等级聚合统计（projectId=1 占位） */
async function fetchAggregate() {
  try {
    const { data } = await aggregateAlerts(1)
    aggregate.value = data
  } catch (e) {
    aggregate.value = []
  }
}

/** 重置查询条件并刷新列表 */
function handleReset() {
  query.level = ''
  query.status = ''
  fetchList()
}

/** 提交按钮 loading 状态，防止重复提交 */
const submitting = ref(false)
/** 预警提交弹窗可见性 */
const dialogVisible = ref(false)
/** 表单引用（用于校验） */
const formRef = ref<any>()
/** 预警提交表单数据 */
const form = reactive<AlertDispatchDTO>({
  alertType: 'BUDGET',
  alertLevel: 'YELLOW',
  title: '',
  content: '',
})
/** 表单校验规则 */
const formRules = {
  alertType: [{ required: true, message: t('execution.alert.rules.alertTypeRequired'), trigger: 'change' }],
  alertLevel: [{ required: true, message: t('execution.alert.rules.alertLevelRequired'), trigger: 'change' }],
  title: [{ required: true, message: t('execution.alert.rules.titleRequired'), trigger: 'blur' }],
}

/** 打开新增预警弹窗，重置表单为默认值 */
function openCreate() {
  Object.assign(form, {
    alertType: 'BUDGET',
    alertLevel: 'YELLOW',
    title: '',
    content: '',
  })
  dialogVisible.value = true
}

/** 提交预警，成功后关闭弹窗并刷新列表 */
async function handleSubmit() {
  try {
    submitting.value = true
    await formRef.value?.validate()
    await submitAlert(form)
    showSuccess(t('execution.alert.messages.submitted'))
    dialogVisible.value = false
    fetchList()
  } catch (e) {
    // 拦截器已弹错，保持弹窗打开
    handleError(e, 'handleSubmit')
  } finally {
    submitting.value = false
  }
}

/**
 * 立即分发指定预警
 * @param row 预警分发记录
 */
async function handleDispatch(row: AlertDispatchVO) {
  const confirmed = await confirmAction(
    t('execution.alert.messages.confirmDispatch', { code: row.alertCode }),
    t('common.tip'),
  )
  if (!confirmed) return
  try {
    const ok = await dispatchAlertNow(row.id)
    if (ok) {
      showSuccess(t('execution.alert.messages.dispatchSuccess'))
    } else {
      ElMessage.error(t('execution.alert.messages.dispatchFailed'))
    }
    fetchList()
  } catch (e) {
    handleError(e, 'handleDispatch')
  }
}

/** 重试最多 3 次失败的预警分发 */
async function handleRetry() {
  try {
    const n = await retryFailedAlerts(3)
    showSuccess(t('execution.alert.messages.retrySuccess', { count: n }))
    fetchList()
  } catch (e) {
    handleError(e, 'handleRetry')
  }
}

/**
 * 取消预警（需填写取消原因）
 * @param row 预警分发记录
 */
async function handleCancel(row: AlertDispatchVO) {
  try {
    const { value: reason } = await ElMessageBox.prompt(t('execution.alert.messages.cancelPrompt'), t('execution.alert.messages.cancelTitle'), {
      inputPattern: /.+/,
      inputErrorMessage: t('execution.alert.messages.cancelReasonRequired'),
    })
    await cancelAlert(row.id, reason)
    showSuccess(t('execution.alert.messages.canceled'))
    fetchList()
  } catch (e) {
    // 用户取消输入时不处理
    if (e !== 'cancel') {
      handleError(e, 'handleCancel')
    }
  }
}

/**
 * 查询并弹窗展示指定等级的触达角色
 * @param level 预警等级（YELLOW/RED/NORMAL）
 */
async function handleResolveRoles(level: string) {
  const { data } = await resolveAlertRoles(level)
  const roles = (data as unknown as AlertResolveRolesVO).roles || (data as unknown as string[])
  ElMessageBox.alert(Array.isArray(roles) ? roles.join(' / ') : String(roles), t('execution.alert.messages.rolesTitle', { level }), {
    type: 'info',
  })
}

/** 页面挂载时并行加载列表与聚合统计 */
onMounted(() => {
  fetchList()
  fetchAggregate()
})
</script>

<template>
  <PageLayout>
    <!-- 工具栏：提交/重试按钮 + 等级状态筛选 -->
    <template #toolbar>
      <div class="flex items-center justify-between w-full">
        <div class="flex gap-2">
          <el-button type="primary" @click="openCreate">
            <el-icon><Plus /></el-icon>{{ $t('execution.alert.buttons.submit') }}
          </el-button>
          <el-button type="warning" @click="handleRetry">
            <el-icon><Refresh /></el-icon>{{ $t('execution.alert.buttons.retryFailed') }}
          </el-button>
        </div>
        <div class="flex gap-2">
          <el-select v-model="query.level" :placeholder="$t('execution.alert.search.level')" clearable style="width: 120px" @change="fetchList">
            <el-option :label="$t('execution.alert.level.YELLOW')" value="YELLOW" />
            <el-option :label="$t('execution.alert.level.RED')" value="RED" />
            <el-option :label="$t('execution.alert.level.NORMAL')" value="NORMAL" />
          </el-select>
          <el-select v-model="query.status" :placeholder="$t('execution.alert.search.status')" clearable style="width: 140px" @change="fetchList">
            <el-option :label="$t('execution.alert.status.PENDING')" value="PENDING" />
            <el-option :label="$t('execution.alert.status.SENT')" value="SENT" />
            <el-option :label="$t('execution.alert.status.FAILED')" value="FAILED" />
            <el-option :label="$t('execution.alert.status.CANCELLED')" value="CANCELLED" />
          </el-select>
          <el-button @click="handleReset">{{ $t('common.reset') }}</el-button>
        </div>
      </div>
    </template>

    <!-- 聚合统计 -->
    <el-row :gutter="12" class="mb-3">
      <el-col v-for="agg in aggregate" :key="agg.alertType + agg.alertLevel" :span="6">
        <el-card shadow="hover">
          <div class="text-sm text-gray-500">{{ typeMap[agg.alertType] || agg.alertType }}</div>
          <div class="text-xl font-bold mt-1">
            <StatusTag
              :label="levelMap[agg.alertLevel as keyof typeof levelMap]?.label || agg.alertLevel"
              :type="levelMap[agg.alertLevel as keyof typeof levelMap]?.type || 'info'"
            />
            <span class="ml-2">{{ agg.count }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 预警分发列表（P3-1: 已迁移到 VirtualTable，支持虚拟滚动 + 自定义插槽） -->
    <VirtualTable
      :data="list as Record<string, unknown>[]"
      :columns="alertColumns"
      :loading="loading"
      :height="520"
    >
      <template #col-alertType="{ row }">
        {{ typeMap[(row as AlertDispatchVO).alertType] || (row as AlertDispatchVO).alertType }}
      </template>
      <template #col-alertLevel="{ row }">
        <el-link type="primary" @click="handleResolveRoles((row as AlertDispatchVO).alertLevel)">
          <StatusTag
            :label="levelMap[(row as AlertDispatchVO).alertLevel as keyof typeof levelMap]?.label || (row as AlertDispatchVO).alertLevel"
            :type="levelMap[(row as AlertDispatchVO).alertLevel as keyof typeof levelMap]?.type || 'info'"
          />
        </el-link>
      </template>
      <template #col-status="{ row }">
        <StatusTag
          :label="statusMap[(row as AlertDispatchVO).status as keyof typeof statusMap]?.label || (row as AlertDispatchVO).status"
          :type="statusMap[(row as AlertDispatchVO).status as keyof typeof statusMap]?.type || 'info'"
        />
      </template>
      <template #col-actions="{ row }">
        <el-button
          v-if="(row as AlertDispatchVO).status === 'PENDING' || (row as AlertDispatchVO).status === 'FAILED'"
          type="primary"
          size="small"
          link
          @click="handleDispatch(row as AlertDispatchVO)"
        >
          {{ $t('execution.alert.buttons.dispatchNow') }}
        </el-button>
        <el-button
          v-if="(row as AlertDispatchVO).status === 'PENDING' || (row as AlertDispatchVO).status === 'FAILED'"
          type="danger"
          size="small"
          link
          @click="handleCancel(row as AlertDispatchVO)"
        >
          {{ $t('execution.alert.buttons.cancel') }}
        </el-button>
      </template>
    </VirtualTable>

    <!-- 提交预警弹窗 -->
    <el-dialog v-model="dialogVisible" :title="$t('execution.alert.dialog.submitTitle')" width="600px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item :label="$t('execution.alert.dialog.alertType')" prop="alertType">
          <el-select v-model="form.alertType" style="width: 100%">
            <el-option v-for="key in Object.keys(typeMap)" :key="key" :label="typeMap[key as keyof typeof typeMap]" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('execution.alert.dialog.alertLevel')" prop="alertLevel">
          <el-radio-group v-model="form.alertLevel">
            <el-radio value="YELLOW">{{ $t('execution.alert.level.YELLOW') }}</el-radio>
            <el-radio value="RED">{{ $t('execution.alert.level.RED') }}</el-radio>
            <el-radio value="NORMAL">{{ $t('execution.alert.level.NORMAL') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('execution.alert.dialog.title')" prop="title">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item :label="$t('execution.alert.dialog.content')">
          <el-input v-model="form.content" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item :label="$t('execution.alert.dialog.targetRole')">
          <el-input v-model="form.targetRole" :placeholder="$t('execution.alert.dialog.targetRolePlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ $t('common.submit') }}</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
