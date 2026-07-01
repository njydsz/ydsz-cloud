<script setup lang="ts">
/**
 * 每日对账 (P6)
 *
 * 对账维度: 成本/收入/回款/开票/工时/利润
 * 状态: OK / WARN / ERROR
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  runDailyReconcile,
  queryReconcileByDateRange,
  aggregateReconcileStatus,
} from '@/api/execution/reconcile'
import type {
  DailyReconcileVO,
  DailyReconcileAggregateVO,
} from '@/api/execution/reconcile/types'
import { PC } from '@/constants/permissionCodes'

const loading = ref(false)
const list = ref<DailyReconcileVO[]>([])
const aggregate = ref<DailyReconcileAggregateVO[]>([])
const query = reactive({
  from: '',
  to: '',
  status: '',
})

const statusMap = {
  OK: { label: '正常', type: 'success' as const },
  WARN: { label: '警告', type: 'warning' as const },
  ERROR: { label: '异常', type: 'danger' as const },
}

const typeMap: Record<string, string> = {
  COST: '成本',
  REVENUE: '收入',
  PAYMENT: '回款',
  INVOICE: '开票',
  LABOR: '工时',
  PROFIT: '利润',
}

function defaultRange() {
  const today = new Date()
  const from = new Date(today.getTime() - 7 * 24 * 60 * 60 * 1000)
  query.from = from.toISOString().slice(0, 10)
  query.to = today.toISOString().slice(0, 10)
}

async function fetchList() {
  loading.value = true
  try {
    list.value = await queryReconcileByDateRange({
      from: query.from || undefined,
      to: query.to || undefined,
      status: query.status || undefined,
    }).then((r) => r.data as DailyReconcileVO[])
  } finally {
    loading.value = false
  }
}

async function fetchAggregate() {
  try {
    aggregate.value = await aggregateReconcileStatus({
      from: query.from || undefined,
      to: query.to || undefined,
    }).then((r) => r.data as DailyReconcileAggregateVO[])
  } catch {
    aggregate.value = []
  }
}

function handleReset() {
  defaultRange()
  query.status = ''
  fetchList()
  fetchAggregate()
}

async function handleRun() {
  const n = await runDailyReconcile()
  ElMessage.success(`已生成 ${n} 条对账记录`)
  fetchList()
  fetchAggregate()
}

onMounted(() => {
  defaultRange()
  fetchList()
  fetchAggregate()
})
</script>

<template>
  <PageLayout
    v-model:query="query"
    :list="list"
    :total="list.length"
    :loading="loading"
    :hide-pagination="true"
    @query="fetchList()"
    @reset="handleReset"
    @refresh="() => { fetchList(); fetchAggregate(); }"
  >
    <template #search>
      <el-form-item label="开始日期">
        <el-date-picker v-model="query.from" type="date" value-format="YYYY-MM-DD" style="width: 160px" />
      </el-form-item>
      <el-form-item label="结束日期">
        <el-date-picker v-model="query.to" type="date" value-format="YYYY-MM-DD" style="width: 160px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.EXECUTION_RECONCILE_RUN]" type="primary" :icon="'Refresh'" @click="handleRun">
        立即对账
      </el-button>
    </template>

    <!-- 状态聚合 -->
    <el-row :gutter="12" class="mb-3">
      <el-col v-for="agg in aggregate" :key="agg.status" :span="8">
        <el-card shadow="hover">
          <div class="text-sm text-gray-500">
            <StatusTag :value="agg.status" :map="statusMap" />
            <span class="ml-2">共 {{ agg.count }} 条</span>
          </div>
          <div class="text-lg font-bold mt-1">
            累计差异：{{ Number(agg.totalDiff || 0).toFixed(2) }}
          </div>
        </el-card>
      </el-col>
    </el-row>

    <template #table>
      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="reconcileDate" title="对账日期" width="120" />
        <vxe-column field="reconcileType" title="维度" width="100">
          <template #default="{ row }">{{ typeMap[row.reconcileType] || row.reconcileType }}</template>
        </vxe-column>
        <vxe-column field="initiationId" title="项目 ID" width="100" />
        <vxe-column field="expectedAmount" title="应有金额" width="140" align="right">
          <template #default="{ row }">{{ Number(row.expectedAmount || 0).toFixed(2) }}</template>
        </vxe-column>
        <vxe-column field="actualAmount" title="实有金额" width="140" align="right">
          <template #default="{ row }">{{ Number(row.actualAmount || 0).toFixed(2) }}</template>
        </vxe-column>
        <vxe-column field="diffAmount" title="差异" width="140" align="right">
          <template #default="{ row }">
            <span :class="Number(row.diffAmount) !== 0 ? 'text-red-500 font-bold' : ''">
              {{ Number(row.diffAmount || 0).toFixed(2) }}
            </span>
          </template>
        </vxe-column>
        <vxe-column field="diffPct" title="差异率" width="100" align="right">
          <template #default="{ row }">
            {{ ((Number(row.diffPct || 0)) * 100).toFixed(2) }}%
          </template>
        </vxe-column>
        <vxe-column field="status" title="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column field="detail" title="说明" min-width="240" show-overflow />
        <vxe-column field="createdAt" title="对账时间" width="170" />
      </vxe-table>
    </template>
  </PageLayout>
</template>
