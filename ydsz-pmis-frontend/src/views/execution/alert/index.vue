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

const loading = ref(false)
const list = ref<AlertDispatchVO[]>([])
const aggregate = ref<AlertAggregateVO[]>([])
const query = reactive({
  level: '' as '' | 'YELLOW' | 'RED' | 'NORMAL',
  status: '' as '' | 'PENDING' | 'SENT' | 'FAILED' | 'CANCELLED',
})

const levelMap = {
  YELLOW: { label: '黄色', type: 'warning' as const },
  RED: { label: '红色', type: 'danger' as const },
  NORMAL: { label: '通知', type: 'info' as const },
}

const statusMap = {
  PENDING: { label: '待分发', type: 'warning' as const },
  SENT: { label: '已发送', type: 'success' as const },
  FAILED: { label: '失败', type: 'danger' as const },
  CANCELLED: { label: '已取消', type: 'info' as const },
}

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

async function fetchAggregate() {
  try {
    const { data } = await aggregateAlerts(1)
    aggregate.value = data
  } catch (e) {
    aggregate.value = []
  }
}

function handleReset() {
  query.level = ''
  query.status = ''
  fetchList()
}

const dialogVisible = ref(false)
const form = reactive<AlertDispatchDTO>({
  alertType: 'BUDGET',
  alertLevel: 'YELLOW',
  title: '',
  content: '',
})
const formRules = {
  alertType: [{ required: true, message: '预警类型必填', trigger: 'change' }],
  alertLevel: [{ required: true, message: '预警等级必填', trigger: 'change' }],
  title: [{ required: true, message: '标题必填', trigger: 'blur' }],
}

function openCreate() {
  Object.assign(form, {
    alertType: 'BUDGET',
    alertLevel: 'YELLOW',
    title: '',
    content: '',
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  await submitAlert(form)
  ElMessage.success('预警已提交')
  dialogVisible.value = false
  fetchList()
}

async function handleDispatch(row: AlertDispatchVO) {
  await ElMessageBox.confirm(`确认立即分发预警 ${row.alertCode}?`, '提示', { type: 'warning' })
  const ok = await dispatchAlertNow(row.id)
  ElMessage[ok ? 'success' : 'error'](ok ? '分发成功' : '分发失败')
  fetchList()
}

async function handleRetry() {
  const n = await retryFailedAlerts(3)
  ElMessage.success(`已重发 ${n} 条预警`)
  fetchList()
}

async function handleCancel(row: AlertDispatchVO) {
  const { value: reason } = await ElMessageBox.prompt('请输入取消原因', '取消预警', {
    inputPattern: /.+/,
    inputErrorMessage: '原因不能为空',
  })
  await cancelAlert(row.id, reason)
  ElMessage.success('已取消')
  fetchList()
}

async function handleResolveRoles(level: string) {
  const { data } = await resolveAlertRoles(level)
  const roles = (data as unknown as AlertResolveRolesVO).roles || (data as unknown as string[])
  ElMessageBox.alert(Array.isArray(roles) ? roles.join(' / ') : String(roles), `${level} 等级触达角色`, {
    type: 'info',
  })
}

onMounted(() => {
  fetchList()
  fetchAggregate()
})
</script>

<template>
  <PageLayout>
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

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="alertCode" label="预警编号" width="220" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          {{ typeMap[row.alertType] || row.alertType }}
        </template>
      </el-table-column>
      <el-table-column label="等级" width="80">
        <template #default="{ row }">
          <el-link type="primary" @click="handleResolveRoles(row.alertLevel)">
            <StatusTag
              :label="levelMap[row.alertLevel as keyof typeof levelMap]?.label || row.alertLevel"
              :type="levelMap[row.alertLevel as keyof typeof levelMap]?.type || 'info'"
            />
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
      <el-table-column prop="targetRole" label="触达角色" width="160" />
      <el-table-column prop="pushChannels" label="渠道" width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <StatusTag
            :label="statusMap[row.status as keyof typeof statusMap]?.label || row.status"
            :type="statusMap[row.status as keyof typeof statusMap]?.type || 'info'"
          />
        </template>
      </el-table-column>
      <el-table-column prop="retryCount" label="重试" width="60" />
      <el-table-column prop="dispatchedAt" label="触发时间" width="160" />
      <el-table-column prop="failReason" label="失败原因" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
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
      </el-table-column>
    </el-table>

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
        <el-button type="primary" @click="handleSubmit">提交</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
