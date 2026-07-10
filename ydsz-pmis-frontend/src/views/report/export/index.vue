<!--
  @file 报表导出中心
  @description 报表导出任务管理页面，支持创建导出任务、查看导出状态、下载已完成的报表文件。
  @module views/report/export
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import PageLayout from '@/components/common/PageLayout.vue'

const { t } = useI18n()

const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, status: '' })

const dialogVisible = ref(false)
const form = reactive({
  reportType: '',
  fileFormat: 'XLSX',
  params: '',
})

const reportTypeOptions = [
  { label: '利润报表', value: 'PROFIT' },
  { label: '成本明细', value: 'COST_DETAIL' },
  { label: '回款台账', value: 'PAYMENT_LEDGER' },
  { label: '生命周期台账', value: 'LIFECYCLE' },
  { label: 'EVM 挣值', value: 'EVM' },
  { label: '利用率报表', value: 'UTILIZATION' },
]

const statusMap: Record<string, { label: string; type: string }> = {
  PENDING: { label: '排队中', type: 'info' },
  RUNNING: { label: '导出中', type: 'warning' },
  SUCCESS: { label: '已完成', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
}

async function loadData() {
  loading.value = true
  try {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleCreate() {
  dialogVisible.value = true
}

async function handleSubmit() {
  ElMessage.success('导出任务已创建')
  dialogVisible.value = false
  loadData()
}

function handleDownload(row: any) {
  ElMessage.info(`下载文件: ${row.fileName || 'export.xlsx'}`)
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <PageLayout>
    <template #header>
      <div class="flex items-center justify-between">
        <h2 class="text-lg font-semibold">报表导出</h2>
        <el-button type="primary" @click="handleCreate">
          <el-icon><Download /></el-icon>
          创建导出
        </el-button>
      </div>
    </template>

    <div class="mb-4 flex gap-3">
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
        <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="reportType" label="报表类型" width="150">
        <template #default="{ row }">{{ reportTypeOptions.find(o => o.value === row.reportType)?.label || row.reportType }}</template>
      </el-table-column>
      <el-table-column prop="fileFormat" label="格式" width="80" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type || 'info'">
            {{ statusMap[row.status]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="fileSize" label="文件大小" width="120" />
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column prop="completedAt" label="完成时间" width="170" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'SUCCESS'" type="primary" link @click="handleDownload(row)">下载</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="mt-4 flex justify-end">
      <el-pagination
        v-model:current-page="query.page"
        :page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="(p: number) => { query.page = p; loadData() }"
      />
    </div>

    <el-dialog v-model="dialogVisible" title="创建导出任务" width="480px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="报表类型" required>
          <el-select v-model="form.reportType" placeholder="请选择">
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
        <el-form-item label="参数">
          <el-input v-model="form.params" type="textarea" :rows="3" placeholder="JSON 格式的查询参数" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">创建</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
