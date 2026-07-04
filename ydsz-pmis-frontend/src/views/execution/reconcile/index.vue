<!--
  @file 执行-财务对账
  @description 每日对账管理页面：支持按日期区间查询对账明细(成本/收入/回款/开票/工时/利润)、状态聚合统计(OK/WARN/ERROR)、手动触发对账重算，对应路由 /execution/reconcile
  @module views/execution/reconcile
-->
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
import { isHandledError } from '@/utils/error'
import { PC } from '@/constants/permissionCodes'

/** 对账按钮 loading 状态，防止重复触发 */
const running = ref(false)
// 列表查询状态
const loading = ref(false)
const list = ref<DailyReconcileVO[]>([])
const aggregate = ref<DailyReconcileAggregateVO[]>([])
const query = reactive({
  from: '',
  to: '',
  status: '',
})

// 状态字典：对账结果状态映射到标签文案与色值
const statusMap = {
  OK: { label: '正常', type: 'success' as const },
  WARN: { label: '警告', type: 'warning' as const },
  ERROR: { label: '异常', type: 'danger' as const },
}

// 对账维度字典：成本/收入/回款/开票/工时/利润
const typeMap: Record<string, string> = {
  COST: '成本',
  REVENUE: '收入',
  PAYMENT: '回款',
  INVOICE: '开票',
  LABOR: '工时',
  PROFIT: '利润',
}

/** 初始化默认查询区间：近 7 天（from = 今天 - 7 天，to = 今天） */
function defaultRange() {
  const today = new Date()
  const from = new Date(today.getTime() - 7 * 24 * 60 * 60 * 1000)
  query.from = from.toISOString().slice(0, 10)
  query.to = today.toISOString().slice(0, 10)
}

/** 拉取对账明细列表：按日期区间与状态过滤 */
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

/** 拉取对账状态聚合统计：按日期区间汇总 OK/WARN/ERROR 数量与差异总额 */
async function fetchAggregate() {
  try {
    aggregate.value = await aggregateReconcileStatus({
      from: query.from || undefined,
      to: query.to || undefined,
    }).then((r) => r.data as DailyReconcileAggregateVO[])
  } catch (e) {
    aggregate.value = []
    if (!isHandledError(e)) {
      ElMessage.error('对账聚合数据加载失败，请刷新重试')
    }
  }
}

/** 重置查询条件为默认区间并刷新列表与聚合数据 */
function handleReset() {
  defaultRange()
  query.status = ''
  fetchList()
  fetchAggregate()
}

/** 手动触发每日对账重算，生成对账记录后刷新列表与聚合数据 */
async function handleRun() {
  try {
    running.value = true
    const n = await runDailyReconcile()
    ElMessage.success(`已生成 ${n} 条对账记录`)
    fetchList()
    fetchAggregate()
  } catch {
    // 拦截器已弹错
  } finally {
    running.value = false
  }
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
      <el-button v-permission="[PC.EXECUTION_RECONCILE_RUN]" type="primary" :icon="'Refresh'" :loading="running" @click="handleRun">
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

    <template #table="scope">
      <vxe-table :data="list" :loading="loading" border stripe :height="scope.tableProps.height" :scroll-y="scope.tableProps.scrollY">
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
