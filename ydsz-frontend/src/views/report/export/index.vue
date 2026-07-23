<!--
  @file 报表导出中心（下载中心）
  @description 对接异步导出 API，支持创建导出任务、实时进度轮询、下载已完成文件、删除记录。
  @module views/report/export
-->
<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import PageLayout from '@/components/common/PageLayout.vue'
import {
  submitExport,
  getExportRecords,
  getDownloadUrl,
  deleteExportRecord,
  type ExportRecord,
  type ExportStatus,
} from '@/api/report/export'
import { isHandledError } from '@/utils/error'

const { t } = useI18n()

// ===== 状态 =====
const loading = ref(false)
const list = ref<ExportRecord[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, status: '' as string })

// 创建导出对话框
const dialogVisible = ref(false)
const submitLoading = ref(false)
const form = reactive({
  exportType: '',
  fileFormat: 'XLSX',
  params: '{}',
})

// 报表类型选项（与后端 AsyncExportServiceImpl.fetchReportData 对齐）
const reportTypeOptions = [
  { label: '驾驶舱 KPI', value: 'COCKPIT' },
  { label: '利润报表', value: 'PROFIT' },
  { label: '回款台账', value: 'PAYMENT' },
  { label: '成本明细', value: 'COST' },
  { label: '生命周期台账', value: 'LIFECYCLE' },
  { label: '立项信息', value: 'PROJECT' },
]

// 状态映射（与后端 ydsz_export_record.status 对齐）
const statusMap: Record<ExportStatus, { label: string; type: 'info' | 'warning' | 'success' | 'danger' | '' }> = {
  PENDING: { label: '排队中', type: 'info' },
  GENERATING: { label: '生成中', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
}

// 进度模拟映射
const progressMap: Record<ExportStatus, number> = {
  PENDING: 0,
  GENERATING: 50,
  COMPLETED: 100,
  FAILED: 0,
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

// 格式化时间
function formatTime(time?: string): string {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

// 是否有进行中的任务
const hasInProgress = computed(() =>
  list.value.some(r => r.status === 'PENDING' || r.status === 'GENERATING'),
)

// ===== 数据加载 =====
async function loadData() {
  loading.value = true
  try {
    const { data } = await getExportRecords(query.page, query.size)
    if (data) {
      // 后端返回 Spring Data Page 格式，content 在 data.content 中
      const pageData = data as any
      list.value = pageData.content || []
      total.value = pageData.totalElements || 0
    }
  } catch (e) {
    if (!isHandledError(e)) {
      ElMessage.error('加载导出记录失败')
    }
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// ===== 进度轮询 =====
let pollTimer: ReturnType<typeof setInterval> | null = null

function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    if (hasInProgress.value) {
      // 静默刷新，不触发 loading
      try {
        const { data } = await getExportRecords(query.page, query.size)
        if (data) {
          const pageData = data as any
          list.value = pageData.content || []
          total.value = pageData.totalElements || 0
        }
      } catch {
        // 静默失败，不打扰用户
      }
    } else {
      // 没有进行中的任务，停止轮询
      stopPolling()
    }
  }, 5000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

// ===== 操作 =====
function handleCreate() {
  form.exportType = ''
  form.fileFormat = 'XLSX'
  form.params = '{}'
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.exportType) {
    ElMessage.warning('请选择报表类型')
    return
  }

  let params: Record<string, unknown> = {}
  try {
    params = JSON.parse(form.params)
  } catch {
    ElMessage.warning('参数格式不正确，请输入有效的 JSON')
    return
  }

  params.fileFormat = form.fileFormat

  submitLoading.value = true
  try {
    const { data } = await submitExport(form.exportType, params)
    if (data) {
      ElMessage.success('导出任务已创建')
      dialogVisible.value = false
      await loadData()
      startPolling()
    }
  } catch (e) {
    if (!isHandledError(e)) {
      ElMessage.error('创建导出任务失败')
    }
  } finally {
    submitLoading.value = false
  }
}

async function handleDownload(row: ExportRecord) {
  try {
    const { data } = await getDownloadUrl(row.id)
    if (data?.url) {
      window.open(data.url, '_blank')
    } else {
      ElMessage.warning('文件不可用或已过期')
    }
  } catch (e) {
    if (!isHandledError(e)) {
      ElMessage.error('获取下载链接失败')
    }
  }
}

async function handleDelete(row: ExportRecord) {
  try {
    await ElMessageBox.confirm('确认删除该导出记录？', '提示', {
      type: 'warning',
    })
    await deleteExportRecord(row.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (e) {
    if (e !== 'cancel' && !isHandledError(e)) {
      ElMessage.error('删除失败')
    }
  }
}

function handleQuery() {
  query.page = 1
  loadData()
}

function handlePageChange(p: number) {
  query.page = p
  loadData()
}

// ===== 生命周期 =====
onMounted(async () => {
  await loadData()
  if (hasInProgress.value) {
    startPolling()
  }
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <PageLayout>
    <template #header>
      <div class="flex items-center justify-between">
        <h2 class="text-lg font-semibold">{{ t('route.reportExport') }}</h2>
        <el-button type="primary" @click="handleCreate">
          <el-icon><Download /></el-icon>
          {{ t('common.createExport') }}
        </el-button>
      </div>
    </template>

    <!-- 筛选区 -->
    <div class="mb-4 flex gap-3">
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px" @change="handleQuery">
        <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-button type="primary" @click="handleQuery">查询</el-button>
      <el-button v-if="hasInProgress" :loading="true" link type="warning">
        <el-icon class="is-loading"><Loading /></el-icon>
        正在轮询进度...
      </el-button>
    </div>

    <!-- 记录列表 -->
    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="export_type" label="报表类型" width="150">
        <template #default="{ row }">
          {{ reportTypeOptions.find(o => o.value === row.export_type)?.label || row.export_type }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status as ExportStatus]?.type || 'info'">
            {{ statusMap[row.status as ExportStatus]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" width="180">
        <template #default="{ row }">
          <el-progress
            v-if="row.status === 'PENDING' || row.status === 'GENERATING'"
            :percentage="progressMap[row.status as ExportStatus] || 0"
            :status="row.status === 'GENERATING' ? 'warning' : 'info'"
            :stroke-width="14"
            :text-inside="true"
          />
          <span v-else-if="row.status === 'COMPLETED'" class="text-green-600">100%</span>
          <el-tooltip v-else-if="row.status === 'FAILED'" :content="row.error_message || '导出失败'" placement="top">
            <span class="text-red-500 cursor-help">失败</span>
          </el-tooltip>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="文件大小" width="120">
        <template #default="{ row }">{{ formatFileSize(row.file_size) }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
      </el-table-column>
      <el-table-column label="完成时间" width="170">
        <template #default="{ row }">{{ formatTime(row.completed_at) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'COMPLETED'"
            type="primary"
            link
            @click="handleDownload(row)"
          >
            下载
          </el-button>
          <el-button
            v-if="row.status === 'FAILED'"
            type="warning"
            link
            @click="handleCreate"
          >
            重试
          </el-button>
          <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="mt-4 flex justify-end">
      <el-pagination
        v-model:current-page="query.page"
        :page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 创建导出对话框 -->
    <el-dialog v-model="dialogVisible" title="创建导出任务" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="报表类型" required>
          <el-select v-model="form.exportType" placeholder="请选择报表类型" style="width: 100%">
            <el-option v-for="o in reportTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件格式" required>
          <el-radio-group v-model="form.fileFormat">
            <el-radio value="XLSX">Excel</el-radio>
            <el-radio value="CSV">CSV</el-radio>
            <el-radio value="PDF">PDF</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="查询参数">
          <el-input
            v-model="form.params"
            type="textarea"
            :rows="4"
            placeholder='JSON 格式的查询参数，如 {"initiationId": 123, "period": "2024-01"}'
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">创建</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
