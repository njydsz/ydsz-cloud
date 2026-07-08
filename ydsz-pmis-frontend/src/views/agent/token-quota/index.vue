<!--
  @fileoverview Token 配额与用量监控仪表盘
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { getSummary, reset } from '@/api/agent/token-quota'
import type { QuotaSummary } from '@/api/agent/token-quota/types'
import { useECharts } from '@/composables/useECharts'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

const loading = ref(false)
const summary = ref<QuotaSummary | null>(null)
const resetting = ref(false)

const gaugeRef = ref<HTMLDivElement | null>(null)
const { setOption: setGaugeOption } = useECharts(gaugeRef)

const usagePercentage = computed(() => summary.value?.usagePercentage ?? 0)
const usageColor = computed(() => {
  const pct = usagePercentage.value
  if (pct >= 90) return '#F56C6C'
  if (pct >= 70) return '#E6A23C'
  return '#67C23A'
})

async function loadSummary() {
  loading.value = true
  try {
    const { data } = await getSummary()
    summary.value = data as QuotaSummary
    renderGauge()
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.tokenQuota.messages.loadFailed'))
  } finally {
    loading.value = false
  }
}

function renderGauge() {
  if (!summary.value) return
  const pct = summary.value.usagePercentage
  setGaugeOption({
    series: [{
      type: 'gauge',
      startAngle: 200,
      endAngle: -20,
      min: 0,
      max: 100,
      progress: {
        show: true,
        width: 30,
        itemStyle: { color: usageColor.value },
      },
      axisLine: { lineStyle: { width: 30 } },
      pointer: { show: true },
      detail: {
        valueAnimation: true,
        formatter: '{value}%',
        fontSize: 28,
        offsetCenter: [0, '70%'],
      },
      data: [{ value: Math.round(pct * 10) / 10 }],
    }],
  })
}

async function handleReset() {
  try {
    await ElMessageBox.confirm(
      t('agent.tokenQuota.messages.resetConfirm'),
      t('common.warning'),
      { type: 'warning' },
    )
    resetting.value = true
    await reset()
    ElMessage.success(t('agent.tokenQuota.messages.resetSuccess'))
    await loadSummary()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || t('agent.tokenQuota.messages.resetFailed'))
    }
  } finally {
    resetting.value = false
  }
}

function formatNumber(n: number): string {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K'
  return String(n)
}

onMounted(() => {
  loadSummary()
})
</script>

<template>
  <div class="token-quota-page" v-loading="loading">
    <el-row :gutter="16">
      <!-- 配额仪表盘 -->
      <el-col :xs="24" :md="10">
        <el-card shadow="never" :header="t('agent.tokenQuota.title')">
          <div ref="gaugeRef" class="gauge-chart" />
          <div class="reset-btn">
            <el-button v-permission="[PC.AGENT_TOKEN_QUOTA_RESET]" type="warning" :icon="'RefreshRight'"
              :loading="resetting" @click="handleReset">
              {{ t('agent.tokenQuota.buttons.reset') }}
            </el-button>
          </div>
        </el-card>
      </el-col>

      <!-- 配额详情 -->
      <el-col :xs="24" :md="14">
        <el-card shadow="never" :header="t('agent.tokenQuota.details')">
          <el-descriptions :column="1" border size="default">
            <el-descriptions-item :label="t('agent.tokenQuota.fields.monthlyQuota')">
              <span class="value">{{ summary ? formatNumber(summary.monthlyQuota) : '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item :label="t('agent.tokenQuota.fields.used')">
              <span class="value" :style="{ color: usageColor }">
                {{ summary ? formatNumber(summary.usedTokens) : '-' }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item :label="t('agent.tokenQuota.fields.remaining')">
              <span class="value" style="color: var(--el-color-success)">
                {{ summary ? formatNumber(summary.remainingTokens) : '-' }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item :label="t('agent.tokenQuota.fields.percentage')">
              <el-progress
                :percentage="Math.min(100, usagePercentage)"
                :color="usageColor"
                :stroke-width="20"
                :text-inside="true"
              />
            </el-descriptions-item>
            <el-descriptions-item :label="t('agent.tokenQuota.fields.period')">
              {{ summary?.period || '-' }}
            </el-descriptions-item>
          </el-descriptions>

          <el-alert
            v-if="usagePercentage >= 90"
            :title="t('agent.tokenQuota.messages.critical')"
            type="error"
            :closable="false"
            style="margin-top: 16px"
          />
          <el-alert
            v-else-if="usagePercentage >= 70"
            :title="t('agent.tokenQuota.messages.warning')"
            type="warning"
            :closable="false"
            style="margin-top: 16px"
          />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style lang="scss" scoped>
.token-quota-page {
  .gauge-chart { width: 100%; height: 300px; }
  .reset-btn { text-align: center; margin-top: 12px; }
  .value { font-size: 20px; font-weight: 600; }
}
</style>
