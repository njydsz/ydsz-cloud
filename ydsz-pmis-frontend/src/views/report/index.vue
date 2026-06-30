<script setup lang="ts">
/**
 * 报表中心
 *
 * 提供项目利润、成本归集、回款台账、生命周期台账等核心报表。
 */
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getProjectProfitReport,
  getCostDetailReport,
  getPaymentLedger,
  getLifecycleReport,
  getProfitSummary,
  getEvmReport,
  getDualRateComparison,
  getRiskDashboard,
} from '@/api/execution/report'

const tab = ref<'profit' | 'cost' | 'payment' | 'lifecycle' | 'summary' | 'evm' | 'dualRate' | 'risk'>('profit')
const loading = ref(false)
const reportData = ref<any>(null)
const summaryData = ref<any[]>([])

const query = reactive({ initiationId: undefined as number | undefined, period: '' })

async function load(target: typeof tab.value) {
  if (!query.initiationId && target !== 'summary' && target !== 'risk') {
    ElMessage.warning('请填写项目 ID')
    return
  }
  loading.value = true
  try {
    let res: any
    switch (target) {
      case 'profit': res = await getProjectProfitReport(query.initiationId!, query.period); break
      case 'cost': res = await getCostDetailReport(query.initiationId!, query.period); break
      case 'payment': res = await getPaymentLedger(query.initiationId!); break
      case 'lifecycle': res = await getLifecycleReport(query.initiationId!); break
      case 'summary': res = await getProfitSummary(); summaryData.value = res?.data || []; return
      case 'evm': res = await getEvmReport(query.initiationId!, query.period); break
      case 'dualRate': res = await getDualRateComparison(query.initiationId!); break
      case 'risk': res = await getRiskDashboard(query.period); break
    }
    reportData.value = res?.data ?? null
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
    reportData.value = null
  } finally {
    loading.value = false
  }
}

function onTabChange(v: any) {
  tab.value = v
  if (v === 'summary' || v === 'risk') {
    load(v)
  } else {
    reportData.value = null
  }
}

function fmtMoney(v: any) {
  if (v == null) return '-'
  return `¥${Number(v).toLocaleString()}`
}
function fmtPct(v: any) {
  if (v == null) return '-'
  return `${(Number(v) * 100).toFixed(2)}%`
}
</script>

<template>
  <div class="report-page">
    <el-card shadow="never" class="query-card">
      <el-form inline :model="query">
        <el-form-item label="项目 ID">
          <el-input-number v-model="query.initiationId" :min="0" :controls="false" />
        </el-form-item>
        <el-form-item label="期间 (YYYY-MM)">
          <el-input v-model="query.period" placeholder="如 2026-07" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="load(tab)">查询</el-button>
          <el-button @click="query.initiationId = undefined; query.period = ''; reportData = null">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="report-card" v-loading="loading">
      <el-tabs :model-value="tab" @update:model-value="onTabChange">
        <el-tab-pane label="项目利润表" name="profit" />
        <el-tab-pane label="成本归集" name="cost" />
        <el-tab-pane label="回款台账" name="payment" />
        <el-tab-pane label="生命周期" name="lifecycle" />
        <el-tab-pane label="跨项目汇总" name="summary" />
        <el-tab-pane label="EVM 报表" name="evm" />
        <el-tab-pane label="双费率对比" name="dualRate" />
        <el-tab-pane label="风险看板" name="risk" />
      </el-tabs>

      <!-- 利润 -->
      <div v-if="tab === 'profit' && reportData" class="grid">
        <div class="kpi"><div class="kpi-label">收入</div><div class="kpi-value money">{{ fmtMoney((reportData as any).revenue) }}</div></div>
        <div class="kpi"><div class="kpi-label">人工成本</div><div class="kpi-value">{{ fmtMoney((reportData as any).laborCost) }}</div></div>
        <div class="kpi"><div class="kpi-label">采购成本</div><div class="kpi-value">{{ fmtMoney((reportData as any).purchaseCost) }}</div></div>
        <div class="kpi"><div class="kpi-label">费用成本</div><div class="kpi-value">{{ fmtMoney((reportData as any).expenseCost) }}</div></div>
        <div class="kpi highlight"><div class="kpi-label">毛利</div><div class="kpi-value money">{{ fmtMoney((reportData as any).grossProfit) }}</div></div>
        <div class="kpi highlight"><div class="kpi-label">毛利率</div><div class="kpi-value">{{ fmtPct((reportData as any).grossMargin) }}</div></div>
      </div>

      <!-- 成本归集 -->
      <div v-else-if="tab === 'cost' && reportData" class="grid">
        <div class="kpi"><div class="kpi-label">总成本</div><div class="kpi-value money">{{ fmtMoney((reportData as any).totalCost) }}</div></div>
        <div class="kpi"><div class="kpi-label">人工占比</div><div class="kpi-value">{{ fmtPct((reportData as any).laborRatio) }}</div></div>
        <div class="kpi"><div class="kpi-label">采购占比</div><div class="kpi-value">{{ fmtPct((reportData as any).purchaseRatio) }}</div></div>
        <div class="kpi"><div class="kpi-label">费用占比</div><div class="kpi-value">{{ fmtPct((reportData as any).expenseRatio) }}</div></div>
      </div>

      <!-- 回款台账 -->
      <div v-else-if="tab === 'payment' && reportData">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="累计开票">{{ fmtMoney((reportData as any).invoicedAmount) }}</el-descriptions-item>
          <el-descriptions-item label="累计回款">{{ fmtMoney((reportData as any).receivedAmount) }}</el-descriptions-item>
          <el-descriptions-item label="未回款">{{ fmtMoney((reportData as any).outstandingAmount) }}</el-descriptions-item>
          <el-descriptions-item label="回款率">{{ fmtPct((reportData as any).collectionRate) }}</el-descriptions-item>
        </el-descriptions>
        <vxe-table :data="(reportData as any).ledgers || []" border style="margin-top: 12px">
          <vxe-column type="seq" title="#" width="50" />
          <vxe-column field="date" title="日期" width="120" />
          <vxe-column field="type" title="类型" width="100" />
          <vxe-column field="code" title="单号" width="160" />
          <vxe-column field="amount" title="金额" width="140" align="right" :formatter="({ cellValue }: any) => fmtMoney(cellValue)" />
          <vxe-column field="remark" title="备注" min-width="200" />
        </vxe-table>
      </div>

      <!-- 生命周期台账 -->
      <div v-else-if="tab === 'lifecycle' && reportData">
        <el-timeline>
          <el-timeline-item
            v-for="(item, idx) in (reportData as any).stages || []"
            :key="idx"
            :timestamp="item.date"
            :type="item.type as any"
          >
            <h4>{{ item.stage }}</h4>
            <p>{{ item.description }}</p>
          </el-timeline-item>
        </el-timeline>
      </div>

      <!-- 跨项目汇总 -->
      <div v-else-if="tab === 'summary'">
        <vxe-table :data="summaryData" border stripe>
          <vxe-column field="initiationId" title="项目 ID" width="100" align="center" />
          <vxe-column field="initiationName" title="项目名称" min-width="200" show-overflow />
          <vxe-column field="revenue" title="收入" width="140" align="right" :formatter="({ cellValue }: any) => fmtMoney(cellValue)" />
          <vxe-column field="totalCost" title="总成本" width="140" align="right" :formatter="({ cellValue }: any) => fmtMoney(cellValue)" />
          <vxe-column field="grossProfit" title="毛利" width="140" align="right" :formatter="({ cellValue }: any) => fmtMoney(cellValue)" />
          <vxe-column field="grossMargin" title="毛利率" width="120" align="right" :formatter="({ cellValue }: any) => fmtPct(cellValue)" />
        </vxe-table>
      </div>

      <!-- EVM -->
      <div v-else-if="tab === 'evm' && reportData" class="grid">
        <div class="kpi"><div class="kpi-label">PV (计划值)</div><div class="kpi-value money">{{ fmtMoney((reportData as any).pv) }}</div></div>
        <div class="kpi"><div class="kpi-label">EV (挣值)</div><div class="kpi-value money">{{ fmtMoney((reportData as any).ev) }}</div></div>
        <div class="kpi"><div class="kpi-label">AC (实际成本)</div><div class="kpi-value money">{{ fmtMoney((reportData as any).ac) }}</div></div>
        <div class="kpi"><div class="kpi-label">BAC (完工预算)</div><div class="kpi-value money">{{ fmtMoney((reportData as any).bac) }}</div></div>
        <div class="kpi highlight"><div class="kpi-label">CPI</div><div class="kpi-value">{{ (reportData as any).cpi?.toFixed?.(2) || '-' }}</div></div>
        <div class="kpi highlight"><div class="kpi-label">SPI</div><div class="kpi-value">{{ (reportData as any).spi?.toFixed?.(2) || '-' }}</div></div>
      </div>

      <!-- 双费率对比 -->
      <div v-else-if="tab === 'dualRate' && reportData" class="grid">
        <div class="kpi"><div class="kpi-label">外部费率总收入</div><div class="kpi-value money">{{ fmtMoney((reportData as any).externalRevenue) }}</div></div>
        <div class="kpi"><div class="kpi-label">内部费率总收入</div><div class="kpi-value money">{{ fmtMoney((reportData as any).internalRevenue) }}</div></div>
        <div class="kpi"><div class="kpi-label">外部毛利</div><div class="kpi-value money">{{ fmtMoney((reportData as any).externalGrossProfit) }}</div></div>
        <div class="kpi"><div class="kpi-label">内部毛利</div><div class="kpi-value money">{{ fmtMoney((reportData as any).internalGrossProfit) }}</div></div>
        <div class="kpi highlight"><div class="kpi-label">外部毛利率</div><div class="kpi-value">{{ fmtPct((reportData as any).externalMargin) }}</div></div>
        <div class="kpi highlight"><div class="kpi-label">内部毛利率</div><div class="kpi-value">{{ fmtPct((reportData as any).internalMargin) }}</div></div>
      </div>

      <!-- 风险看板 -->
      <div v-else-if="tab === 'risk' && reportData" class="grid">
        <div class="kpi"><div class="kpi-label">高风险项目</div><div class="kpi-value">{{ (reportData as any).highRiskCount ?? 0 }}</div></div>
        <div class="kpi"><div class="kpi-label">中风险项目</div><div class="kpi-value">{{ (reportData as any).mediumRiskCount ?? 0 }}</div></div>
        <div class="kpi"><div class="kpi-label">低风险项目</div><div class="kpi-value">{{ (reportData as any).lowRiskCount ?? 0 }}</div></div>
        <div class="kpi"><div class="kpi-label">预警事件总数</div><div class="kpi-value">{{ (reportData as any).alertCount ?? 0 }}</div></div>
      </div>

      <el-empty v-if="!reportData && tab !== 'summary' && tab !== 'risk'" description="请填写项目 ID 并点击查询" />
      <el-empty v-if="!summaryData.length && tab === 'summary'" description="暂无数据" />
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.report-page {
  .query-card { margin-bottom: 16px; }
  .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; }
  .kpi {
    padding: 16px;
    background: var(--el-fill-color-light);
    border-radius: 4px;
    &.highlight { background: var(--el-color-primary-light-9); }
    .kpi-label { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 8px; }
    .kpi-value { font-size: 20px; font-weight: 600; &.money { color: var(--el-color-primary); } }
  }
}
</style>
