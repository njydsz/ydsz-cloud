<!--
  @file 报表订阅管理
  @description 用户可订阅报表，系统按 Cron 计划自动生成并发送报表。
               支持创建、暂停/恢复、删除订阅及查看执行历史。
  @module views/report/subscription
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import PageLayout from '@/components/common/PageLayout.vue'
import {
  createSubscription,
  getSubscriptionList,
  toggleSubscriptionStatus,
  deleteSubscription,
  getSubscriptionHistory,
  type ReportSubscription,
  type SubscriptionStatus,
  type SubscriptionHistory,
  type CreateSubscriptionParams,
} from '@/api/report/subscription'
import { isHandledError } from '@/utils/error'

const { t } = useI18n()

// ===== 状态 =====
const loading = ref(false)
const list = ref<ReportSubscription[]>([])

// 创建对话框
const dialogVisible = ref(false)
const submitLoading = ref(false)
const form = reactive<CreateSubscriptionParams>({
  reportType: '',
  reportName: '',
  cronExpression: '0 0 9 ? * MON',
  deliveryChannels: 'EMAIL',
  deliveryEmails: '',
  params: '{}',
})

// 历史抽屉
const historyVisible = ref(false)
const historyLoading = ref(false)
const historyList = ref<SubscriptionHistory[]>([])
const currentSubscription = ref<ReportSubscription | null>(null)

// 报表类型选项
const reportTypeOptions = [
  { label: '利润报表', value: 'PROFIT' },
  { label: '成本明细', value: 'COST_DETAIL' },
  { label: '回款台账', value: 'PAYMENT_LEDGER' },
  { label: '生命周期台账', value: 'LIFECYCLE' },
  { label: 'EVM 挣值', value: 'EVM' },
  { label: '利用率报表', value: 'UTILIZATION' },
  { label: 'Bench 成本', value: 'BENCH_COST' },
  { label: '风险看板', value: 'RISK_DASHBOARD' },
]

// Cron 预设
const cronPresets = [
  { label: '每天 9:00', value: '0 0 9 * * ?' },
  { label: '每周一 9:00', value: '0 0 9 ? * MON' },
  { label: '每月 1 日 9:00', value: '0 0 9 1 * ?' },
  { label: '每季度 1 日 9:00', value: '0 0 9 1 1,4,7,10 ?' },
  { label: '工作日 9:00', value: '0 0 9 ? * MON-FRI' },
]

// 投递渠道选项
const channelOptions = [
  { label: '邮件', value: 'EMAIL' },
  { label: '钉钉', value: 'DINGTALK' },
  { label: '企业微信', value: 'WECHAT_WORK' },
]

// 状态映射
const statusMap: Record<string, { label: string; type: 'success' | 'warning' | 'info' }> = {
  ACTIVE: { label: '启用', type: 'success' },
  PAUSED: { label: '已暂停', type: 'warning' },
}

// 执行状态映射
const runStatusMap: Record<string, { label: string; type: 'success' | 'danger' | 'warning' | 'info' }> = {
  SUCCESS: { label: '成功', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  RUNNING: { label: '执行中', type: 'warning' },
  SKIPPED: { label: '跳过', type: 'info' },
}

// 格式化时间
function formatTime(time?: string): string {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

// 格式化执行时长
function formatDuration(ms?: number): string {
  if (!ms) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

// 格式化文件大小
function formatFileSize(bytes?: number): string {
  if (!bytes || bytes === 0) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return `${size.toFixed(1)} ${units[i]}`
}

// ===== 数据加载 =====
async function loadData() {
  loading.value = true
  try {
    const { data } = await getSubscriptionList()
    list.value = data || []
  } catch (e) {
    if (!isHandledError(e)) {
      ElMessage.error('加载订阅列表失败')
    }
    list.value = []
  } finally {
    loading.value = false
  }
}

// ===== 操作 =====
function handleCreate() {
  form.reportType = ''
  form.reportName = ''
  form.cronExpression = '0 0 9 ? * MON'
  form.deliveryChannels = 'EMAIL'
  form.deliveryEmails = ''
  form.params = '{}'
  dialogVisible.value = true
}

function handleReportTypeChange(val: string) {
  const opt = reportTypeOptions.find(o => o.value === val)
  if (opt && !form.reportName) {
    form.reportName = `${opt.label} - 定时订阅`
  }
}

async function handleSubmit() {
  if (!form.reportType) {
    ElMessage.warning('请选择报表类型')
    return
  }
  if (!form.reportName) {
    ElMessage.warning('请输入报表名称')
    return
  }
  if (!form.cronExpression) {
    ElMessage.warning('请选择执行频率')
    return
  }

  // 验证 JSON 参数
  try {
    JSON.parse(form.params)
  } catch {
    ElMessage.warning('报表参数格式不正确，请输入有效的 JSON')
    return
  }

  submitLoading.value = true
  try {
    const { data } = await createSubscription({ ...form })
    if (data) {
      ElMessage.success('订阅创建成功')
      dialogVisible.value = false
      await loadData()
    }
  } catch (e) {
    if (!isHandledError(e)) {
      ElMessage.error('创建订阅失败')
    }
  } finally {
    submitLoading.value = false
  }
}

async function handleToggle(row: ReportSubscription) {
  const newStatus: SubscriptionStatus = row.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE'
  try {
    await toggleSubscriptionStatus(row.id, newStatus)
    ElMessage.success(newStatus === 'ACTIVE' ? '已恢复' : '已暂停')
    await loadData()
  } catch (e) {
    if (!isHandledError(e)) {
      ElMessage.error('操作失败')
    }
  }
}

async function handleDelete(row: ReportSubscription) {
  try {
    await ElMessageBox.confirm('确认删除该订阅？删除后不可恢复。', '提示', {
      type: 'warning',
    })
    await deleteSubscription(row.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (e) {
    if (e !== 'cancel' && !isHandledError(e)) {
      ElMessage.error('删除失败')
    }
  }
}

function handleDownloadHistory(url?: string) {
  if (url) {
    window.open(url, '_blank')
  }
}

async function handleViewHistory(row: ReportSubscription) {
  currentSubscription.value = row
  historyVisible.value = true
  historyLoading.value = true
  try {
    const { data } = await getSubscriptionHistory(row.id, 1, 50)
    historyList.value = data || []
  } catch (e) {
    if (!isHandledError(e)) {
      ElMessage.error('加载执行历史失败')
    }
    historyList.value = []
  } finally {
    historyLoading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <PageLayout>
    <template #header>
      <div class="flex items-center justify-between">
        <h2 class="text-lg font-semibold">{{ t('common.reportSubscription') }}</h2>
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          {{ t('common.newSubscription') }}
        </el-button>
      </div>
    </template>

    <!-- 订阅列表 -->
    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="reportName" label="订阅名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="reportType" label="报表类型" width="150">
        <template #default="{ row }">
          {{ reportTypeOptions.find(o => o.value === row.reportType)?.label || row.reportType }}
        </template>
      </el-table-column>
      <el-table-column prop="cronExpression" label="执行频率" width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ cronPresets.find(p => p.value === row.cronExpression)?.label || row.cronExpression }}
        </template>
      </el-table-column>
      <el-table-column prop="deliveryChannels" label="投递渠道" width="120">
        <template #default="{ row }">
          <el-tag
            v-for="ch in (row.deliveryChannels || '').split(',')"
            :key="ch"
            size="small"
            class="mr-1"
          >
            {{ channelOptions.find(c => c.value === ch)?.label || ch }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type || 'info'">
            {{ statusMap[row.status]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastRunAt" label="上次执行" width="170">
        <template #default="{ row }">
          {{ formatTime(row.lastRunAt) }}
          <el-tag
            v-if="row.lastRunStatus"
            size="small"
            :type="runStatusMap[row.lastRunStatus]?.type || 'info'"
            class="ml-1"
          >
            {{ runStatusMap[row.lastRunStatus]?.label || row.lastRunStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleViewHistory(row)">历史</el-button>
          <el-button
            :type="row.status === 'ACTIVE' ? 'warning' : 'success'"
            link
            @click="handleToggle(row)"
          >
            {{ row.status === 'ACTIVE' ? '暂停' : '恢复' }}
          </el-button>
          <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 创建订阅对话框 -->
    <el-dialog v-model="dialogVisible" title="新建报表订阅" width="560px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="报表类型" required>
          <el-select
            v-model="form.reportType"
            placeholder="请选择报表类型"
            style="width: 100%"
            @change="handleReportTypeChange"
          >
            <el-option v-for="o in reportTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="订阅名称" required>
          <el-input v-model="form.reportName" placeholder="如：利润报表 - 周报" />
        </el-form-item>
        <el-form-item label="执行频率" required>
          <el-select v-model="form.cronExpression" placeholder="选择预设或自定义 Cron" style="width: 100%" filterable allow-create>
            <el-option v-for="p in cronPresets" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
          <div class="text-xs text-gray-400 mt-1">支持自定义 Cron 表达式（Quartz 格式）</div>
        </el-form-item>
        <el-form-item label="投递渠道" required>
          <el-select v-model="form.deliveryChannels" placeholder="选择投递渠道" style="width: 100%">
            <el-option v-for="c in channelOptions" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.deliveryChannels === 'EMAIL'" label="收件邮箱">
          <el-input
            v-model="form.deliveryEmails"
            placeholder="多个邮箱用英文逗号分隔，留空则发送给当前用户"
          />
        </el-form-item>
        <el-form-item label="报表参数">
          <el-input
            v-model="form.params"
            type="textarea"
            :rows="4"
            placeholder='JSON 格式，如 {"period": "2024-01", "initiationId": 123}'
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">创建</el-button>
      </template>
    </el-dialog>

    <!-- 执行历史抽屉 -->
    <el-drawer
      v-model="historyVisible"
      :title="`执行历史 - ${currentSubscription?.reportName || ''}`"
      size="50%"
    >
      <el-table v-loading="historyLoading" :data="historyList" border stripe>
        <el-table-column prop="runAt" label="执行时间" width="170">
          <template #default="{ row }">{{ formatTime(row.runAt) }}</template>
        </el-table-column>
        <el-table-column prop="runStatus" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="runStatusMap[row.runStatus]?.type || 'info'">
              {{ runStatusMap[row.runStatus]?.label || row.runStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column label="文件大小" width="120">
          <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.errorMessage" class="text-red-500">{{ row.errorMessage }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-if="row.fileUrl" type="primary" link @click="handleDownloadHistory(row.fileUrl)">
              下载
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </PageLayout>
</template>
