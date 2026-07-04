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
} from '@/api/execution/alert'
import type {
  AlertDispatchVO,
  AlertDispatchDTO,
  AlertAggregateVO,
  AlertResolveRolesVO,
} from '@/api/execution/alert/types'

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
  YELLOW: { label: '黄色', type: 'warning' as const },
  RED: { label: '红色', type: 'danger' as const },
  NORMAL: { label: '通知', type: 'info' as const },
}

/** 分发状态 → 标签/样式映射 */
const statusMap = {
  PENDING: { label: '待分发', type: 'warning' as const },
  SENT: { label: '已发送', type: 'success' as const },
  FAILED: { label: '失败', type: 'danger' as const },
  CANCELLED: { label: '已取消', type: 'info' as const },
}

/** 预警业务类型 → 中文名映射 */
const typeMap: Record<string, string> = {
  BUDGET: '预算',
  RISK: '风险',
  EVM: 'EVM',
  SLA: 'SLA',
  BENCH: 'Bench',
  UTILIZATION: '利用率',
  QUALITY: '质量',
  OTHER: '其他',
}

/** 预警分发列表列配置 */
const alertColumns: ColumnConfig[] = [
  { field: 'alertCode', title: '预警编号', width: 220 },
  { field: 'alertType', title: '类型', width: 100, slot: true },
  { field: 'alertLevel', title: '等级', width: 80, slot: true },
  { field: 'title', title: '标题', width: 220 },
  { field: 'targetRole', title: '触达角色', width: 160 },
  { field: 'pushChannels', title: '渠道', width: 120 },
  { field: 'status', title: '状态', width: 100, slot: true },
  { field: 'retryCount', title: '重试', width: 60 },
  { field: 'dispatchedAt', title: '触发时间', width: 160 },
  { field: 'failReason', title: '失败原因', width: 180 },
  { field: 'actions', title: '操作', width: 220, fixed: 'right', slot: true },
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
/** 预警提交表单数据 */
const form = reactive<AlertDispatchDTO>({
  alertType: 'BUDGET',
  alertLevel: 'YELLOW',
  title: '',
  content: '',
})
/** 表单校验规则 */
const formRules = {
  alertType: [{ required: true, message: '预警类型必填', trigger: 'change' }],
  alertLevel: [{ required: true, message: '预警等级必填', trigger: 'change' }],
  title: [{ required: true, message: '标题必填', trigger: 'blur' }],
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
    await submitAlert(form)
    ElMessage.success('预警已提交')
    dialogVisible.value = false
    fetchList()
  } catch {
    // 拦截器已弹错，保持弹窗打开
  } finally {
    submitting.value = false
  }
}

/**
 * 立即分发指定预警
 * @param row 预警分发记录
 */
async function handleDispatch(row: AlertDispatchVO) {
  await ElMessageBox.confirm(`确认立即分发预警 ${row.alertCode}?`, '提示', { type: 'warning' })
  const ok = await dispatchAlertNow(row.id)
  ElMessage[ok ? 'success' : 'error'](ok ? '分发成功' : '分发失败')
  fetchList()
}

/** 重试最多 3 次失败的预警分发 */
async function handleRetry() {
  const n = await retryFailedAlerts(3)
  ElMessage.success(`已重发 ${n} 条预警`)
  fetchList()
}

/**
 * 取消预警（需填写取消原因）
 * @param row 预警分发记录
 */
async function handleCancel(row: AlertDispatchVO) {
  const { value: reason } = await ElMessageBox.prompt('请输入取消原因', '取消预警', {
    inputPattern: /.+/,
    inputErrorMessage: '原因不能为空',
  })
  await cancelAlert(row.id, reason)
  ElMessage.success('已取消')
  fetchList()
}

/**
 * 查询并弹窗展示指定等级的触达角色
 * @param level 预警等级（YELLOW/RED/NORMAL）
 */
async function handleResolveRoles(level: string) {
  const { data } = await resolveAlertRoles(level)
  const roles = (data as unknown as AlertResolveRolesVO).roles || (data as unknown as string[])
  ElMessageBox.alert(Array.isArray(roles) ? roles.join(' / ') : String(roles), `${level} 等级触达角色`, {
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
            <el-icon><Plus /></el-icon>提交预警
          </el-button>
          <el-button type="warning" @click="handleRetry">
            <el-icon><Refresh /></el-icon>重试失败
          </el-button>
        </div>
        <div class="flex gap-2">
          <el-select v-model="query.level" placeholder="等级" clearable style="width: 120px" @change="fetchList">
            <el-option label="黄色" value="YELLOW" />
            <el-option label="红色" value="RED" />
            <el-option label="通知" value="NORMAL" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="fetchList">
            <el-option label="待分发" value="PENDING" />
            <el-option label="已发送" value="SENT" />
            <el-option label="失败" value="FAILED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
          <el-button @click="handleReset">重置</el-button>
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
          立即分发
        </el-button>
        <el-button
          v-if="(row as AlertDispatchVO).status === 'PENDING' || (row as AlertDispatchVO).status === 'FAILED'"
          type="danger"
          size="small"
          link
          @click="handleCancel(row as AlertDispatchVO)"
        >
          取消
        </el-button>
      </template>
    </VirtualTable>

    <!-- 提交预警弹窗 -->
    <el-dialog v-model="dialogVisible" title="提交预警" width="600px">
      <el-form :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="预警类型" prop="alertType">
          <el-select v-model="form.alertType" style="width: 100%">
            <el-option v-for="t in Object.keys(typeMap)" :key="t" :label="typeMap[t]" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="预警等级" prop="alertLevel">
          <el-radio-group v-model="form.alertLevel">
            <el-radio value="YELLOW">黄色</el-radio>
            <el-radio value="RED">红色</el-radio>
            <el-radio value="NORMAL">通知</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.content" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="触达角色">
          <el-input v-model="form.targetRole" placeholder="留空按 level 自动解析" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
