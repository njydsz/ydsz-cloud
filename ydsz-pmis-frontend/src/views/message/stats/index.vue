<!--
  @fileoverview 消息通知引擎 - 统计看板页面
  @description 消息发送统计的可观测看板：
  - 时间范围选择器（start/end 日期选择器）
  - 总览卡片：总数/成功/失败/重试/死信/已撤回 + 成功率/死信率
  - 通道统计表格：通道/总数/成功/失败/重试/死信/成功率/死信率
  - 回执统计区块：总数/送达/已读/已点击/失败/超时/无回执 + 送达率/已读率
  - 使用 el-card 与 el-statistic 展示
  @module views/message/stats
  @author ydsy-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  getMessageStatsOverview,
  getChannelStats,
  getReceiptStats,
  getFunnelStats,
} from '@/api/message'
import type {
  MessageStatsVO,
  ChannelStatsVO,
  ReceiptStatsVO,
  FunnelStatsVO,
  MessageChannel,
} from '@/api/message/types'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

/** Element Plus el-tag type 联合类型 */
type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary'

/** 日期范围 */
const dateRange = ref<[string, string] | null>(null)

/** 加载中 */
const loading = ref(false)

/** 总览统计 */
const overview = ref<MessageStatsVO | null>(null)
/** 通道统计列表 */
const channelStats = ref<ChannelStatsVO[]>([])
/** 回执统计 */
const receiptStats = ref<ReceiptStatsVO | null>(null)
/** P2-2: 漏斗统计 */
const funnelStats = ref<FunnelStatsVO | null>(null)
/** P2-2: 漏斗过滤-通道 */
const funnelChannel = ref<string>('')
/** P2-2: 漏斗过滤-模板编码 */
const funnelTemplateCode = ref<string>('')

/** 通道文案映射 */
const channelLabelMap: Record<MessageChannel, string> = {
  SMS: t('message.channelSms'),
  EMAIL: t('message.channelEmail'),
  PUSH: t('message.channelPush'),
  IN_APP: t('message.channelInApp'),
  WEBHOOK: t('message.channelWebhook'),
  DINGTALK: t('message.channelDingtalk'),
  WECOM: t('message.channelWecom'),
  FEISHU: t('message.channelFeishu'),
}

/** 通道 Tag 类型映射 */
const channelTagType: Record<MessageChannel, TagType> = {
  SMS: 'primary',
  EMAIL: 'success',
  PUSH: 'warning',
  IN_APP: 'info',
  WEBHOOK: 'info',
  DINGTALK: 'primary',
  WECOM: 'success',
  FEISHU: 'warning',
}

/** 获取通道文案 */
const getChannelLabel = (channel: string): string => {
  return channelLabelMap[channel as MessageChannel] ?? channel
}

/** 获取通道 Tag 类型 */
const getChannelTagType = (channel: string): TagType => {
  return channelTagType[channel as MessageChannel] ?? 'info'
}

/** 格式化比率 */
const formatRate = (rate?: number): string => {
  if (rate === undefined || rate === null) return '-'
  return `${rate.toFixed(2)}%`
}

/** P2-2: 计算漏斗条宽度百分比（相对于 sent） */
const funnelBarWidth = (current?: number, total?: number): string => {
  if (!current || !total || total === 0) return '0%'
  const pct = Math.max(0, Math.min(100, (current / total) * 100))
  return `${pct.toFixed(1)}%`
}

/** 拉取统计数据 */
const fetchStats = async () => {
  let start: string | undefined
  let end: string | undefined
  if (dateRange.value && dateRange.value.length === 2) {
    start = dateRange.value[0]
    end = dateRange.value[1]
  } else {
    ElMessage.warning(t('message.startDate') + '/' + t('message.endDate'))
    return
  }
  loading.value = true
  try {
    const params = { start, end }
    const funnelParams = {
      start,
      end,
      channel: funnelChannel.value || undefined,
      templateCode: funnelTemplateCode.value || undefined,
    }
    const [overviewResp, channelResp, receiptResp, funnelResp] = await Promise.all([
      getMessageStatsOverview(params),
      getChannelStats(params),
      getReceiptStats(params),
      getFunnelStats(funnelParams),
    ])
    overview.value = overviewResp.data ?? null
    channelStats.value = channelResp.data ?? []
    receiptStats.value = receiptResp.data ?? null
    funnelStats.value = funnelResp.data ?? null
  } catch {
    // 静默失败
  } finally {
    loading.value = false
  }
}

/** 快捷选择：最近 7 天 */
const handleQuickSelect7Days = () => {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 6)
  dateRange.value = [formatDate(start), formatDate(end)]
  fetchStats()
}

/** 格式化日期为 YYYY-MM-DD */
const formatDate = (date: Date): string => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

onMounted(() => {
  handleQuickSelect7Days()
})
</script>

<template>
  <div class="message-stats" v-loading="loading">
    <!-- 时间范围选择器 -->
    <el-card shadow="never" class="filter-card">
      <el-form inline>
        <el-form-item :label="t('message.createdAt')">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            :start-placeholder="t('message.startDate')"
            :end-placeholder="t('message.endDate')"
            style="width: 280px"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            v-permission="PC.MESSAGE_LOG_VIEW"
            type="primary"
            @click="fetchStats"
          >
            {{ t('common.search') }}
          </el-button>
          <el-button @click="handleQuickSelect7Days">
            {{ t('common.refresh') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 总览卡片 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <span>{{ t('message.overview') }}</span>
      </template>
      <el-row :gutter="16">
        <el-col :xs="12" :sm="8" :md="6" :lg="3">
          <el-statistic :title="t('message.totalSend')" :value="overview?.total ?? 0" />
        </el-col>
        <el-col :xs="12" :sm="8" :md="6" :lg="3">
          <el-statistic
            :title="t('message.successCount')"
            :value="overview?.success ?? 0"
            :value-style="{ color: 'var(--el-color-success)' }"
          />
        </el-col>
        <el-col :xs="12" :sm="8" :md="6" :lg="3">
          <el-statistic
            :title="t('message.failedCount')"
            :value="overview?.failed ?? 0"
            :value-style="{ color: 'var(--el-color-danger)' }"
          />
        </el-col>
        <el-col :xs="12" :sm="8" :md="6" :lg="3">
          <el-statistic
            :title="t('message.retryingCount')"
            :value="overview?.retry ?? 0"
            :value-style="{ color: 'var(--el-color-warning)' }"
          />
        </el-col>
        <el-col :xs="12" :sm="8" :md="6" :lg="3">
          <el-statistic :title="t('message.deadCount')" :value="overview?.dead ?? 0" />
        </el-col>
        <el-col :xs="12" :sm="8" :md="6" :lg="3">
          <el-statistic :title="t('message.recalledCount')" :value="overview?.recalled ?? 0" />
        </el-col>
        <el-col :xs="12" :sm="8" :md="6" :lg="3">
          <div class="rate-card">
            <div class="rate-title">{{ t('message.successRate') }}</div>
            <div class="rate-value success-color">{{ formatRate(overview?.successRate) }}</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="8" :md="6" :lg="3">
          <div class="rate-card">
            <div class="rate-title">{{ t('message.deadRate') }}</div>
            <div class="rate-value danger-color">{{ formatRate(overview?.deadRate) }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 通道统计 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <span>{{ t('message.channelStats') }}</span>
      </template>
      <el-table :data="channelStats" style="width: 100%" empty-text="-">
        <el-table-column :label="t('message.channel')" width="120">
          <template #default="scope">
            <el-tag size="small" :type="getChannelTagType((scope.row as ChannelStatsVO).channel)">
              {{ getChannelLabel((scope.row as ChannelStatsVO).channel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('message.totalSend')" prop="total" width="120" />
        <el-table-column :label="t('message.successCount')" prop="success" width="120" />
        <el-table-column :label="t('message.failedCount')" prop="failed" width="120" />
        <el-table-column :label="t('message.retryCount')" prop="retry" width="120" />
        <el-table-column :label="t('message.deadCount')" prop="dead" width="120" />
        <el-table-column :label="t('message.successRate')" width="120">
          <template #default="scope">
            {{ formatRate((scope.row as ChannelStatsVO).successRate) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('message.deadRate')" width="120">
          <template #default="scope">
            {{ formatRate((scope.row as ChannelStatsVO).deadRate) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 回执统计 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <span>{{ t('message.receiptStats') }}</span>
      </template>
      <el-row :gutter="16">
        <el-col :xs="12" :sm="8" :md="6" :lg="3">
          <el-statistic :title="t('message.totalSend')" :value="receiptStats?.total ?? 0" />
        </el-col>
        <el-col :xs="12" :sm="8" :md="6" :lg="3">
          <el-statistic
            :title="t('message.deliveredCount')"
            :value="receiptStats?.delivered ?? 0"
            :value-style="{ color: 'var(--el-color-success)' }"
          />
        </el-col>
        <el-col :xs="12" :sm="8" :md="6" :lg="3">
          <el-statistic
            :title="t('message.readCount')"
            :value="receiptStats?.read ?? 0"
            :value-style="{ color: 'var(--el-color-success)' }"
          />
        </el-col>
        <el-col :xs="12" :sm="8" :md="6" :lg="3">
          <el-statistic :title="t('message.clickedCount')" :value="receiptStats?.clicked ?? 0" />
        </el-col>
        <el-col :xs="12" :sm="8" :md="6" :lg="3">
          <el-statistic
            :title="t('message.failedCount')"
            :value="receiptStats?.failed ?? 0"
            :value-style="{ color: 'var(--el-color-danger)' }"
          />
        </el-col>
        <el-col :xs="12" :sm="8" :md="6" :lg="3">
          <el-statistic :title="t('message.timeoutCount')" :value="receiptStats?.timeout ?? 0" />
        </el-col>
        <el-col :xs="12" :sm="8" :md="6" :lg="3">
          <el-statistic :title="t('message.noneCount')" :value="receiptStats?.none ?? 0" />
        </el-col>
        <el-col :xs="12" :sm="8" :md="6" :lg="3">
          <div class="rate-card">
            <div class="rate-title">{{ t('message.deliveryRate') }}</div>
            <div class="rate-value success-color">{{ formatRate(receiptStats?.deliveryRate) }}</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="8" :md="6" :lg="3">
          <div class="rate-card">
            <div class="rate-title">{{ t('message.readRate') }}</div>
            <div class="rate-value success-color">{{ formatRate(receiptStats?.readRate) }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- P2-2: 转化漏斗 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="funnel-header">
          <span>{{ t('message.funnelAnalysis') }}</span>
          <div class="funnel-filter">
            <el-select
              v-model="funnelChannel"
              :placeholder="t('message.channel')"
              clearable
              size="small"
              style="width: 120px"
              @change="fetchStats"
            >
              <el-option label="SMS" value="SMS" />
              <el-option label="EMAIL" value="EMAIL" />
              <el-option label="PUSH" value="PUSH" />
              <el-option label="IN_APP" value="IN_APP" />
              <el-option label="WEBHOOK" value="WEBHOOK" />
              <el-option label="DINGTALK" value="DINGTALK" />
              <el-option label="WECOM" value="WECOM" />
              <el-option label="FEISHU" value="FEISHU" />
            </el-select>
            <el-input
              v-model="funnelTemplateCode"
              :placeholder="t('message.templateCode')"
              clearable
              size="small"
              style="width: 180px; margin-left: 8px"
              @keyup.enter="fetchStats"
              @clear="fetchStats"
            />
          </div>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :xs="12" :sm="6" :md="6">
          <div class="funnel-stage">
            <div class="funnel-stage-title">{{ t('message.funnelSent') }}</div>
            <div class="funnel-stage-value">{{ funnelStats?.sent ?? 0 }}</div>
            <div class="funnel-stage-bar bar-sent" :style="{ width: '100%' }" />
          </div>
        </el-col>
        <el-col :xs="12" :sm="6" :md="6">
          <div class="funnel-stage">
            <div class="funnel-stage-title">{{ t('message.funnelDelivered') }}</div>
            <div class="funnel-stage-value">{{ funnelStats?.delivered ?? 0 }}</div>
            <div class="funnel-stage-rate">{{ formatRate(funnelStats?.deliveryRate) }}</div>
            <div
              class="funnel-stage-bar bar-delivered"
              :style="{ width: funnelBarWidth(funnelStats?.delivered, funnelStats?.sent) }"
            />
          </div>
        </el-col>
        <el-col :xs="12" :sm="6" :md="6">
          <div class="funnel-stage">
            <div class="funnel-stage-title">{{ t('message.funnelRead') }}</div>
            <div class="funnel-stage-value">{{ funnelStats?.read ?? 0 }}</div>
            <div class="funnel-stage-rate">{{ formatRate(funnelStats?.readRate) }}</div>
            <div
              class="funnel-stage-bar bar-read"
              :style="{ width: funnelBarWidth(funnelStats?.read, funnelStats?.sent) }"
            />
          </div>
        </el-col>
        <el-col :xs="12" :sm="6" :md="6">
          <div class="funnel-stage">
            <div class="funnel-stage-title">{{ t('message.funnelClicked') }}</div>
            <div class="funnel-stage-value">{{ funnelStats?.clicked ?? 0 }}</div>
            <div class="funnel-stage-rate">{{ formatRate(funnelStats?.clickRate) }}</div>
            <div
              class="funnel-stage-bar bar-clicked"
              :style="{ width: funnelBarWidth(funnelStats?.clicked, funnelStats?.sent) }"
            />
          </div>
        </el-col>
      </el-row>
      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :xs="12" :sm="6" :md="6">
          <div class="rate-card">
            <div class="rate-title">{{ t('message.funnelDeliveredToRead') }}</div>
            <div class="rate-value success-color">{{ formatRate(funnelStats?.deliveredToReadRate) }}</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="6" :md="6">
          <div class="rate-card">
            <div class="rate-title">{{ t('message.funnelReadToClick') }}</div>
            <div class="rate-value success-color">{{ formatRate(funnelStats?.readToClickRate) }}</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="6" :md="6">
          <div class="rate-card">
            <div class="rate-title">{{ t('message.funnelOverallConversion') }}</div>
            <div class="rate-value danger-color">{{ formatRate(funnelStats?.overallConversionRate) }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.message-stats {
  padding: 16px;
}

.filter-card {
  margin-bottom: 16px;
}

.section-card {
  margin-bottom: 16px;
}

:deep(.el-statistic) {
  margin-bottom: 16px;
}

.rate-card {
  margin-bottom: 16px;

  .rate-title {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-bottom: 4px;
  }

  .rate-value {
    font-size: 20px;
    font-weight: 600;
  }

  .success-color {
    color: var(--el-color-success);
  }

  .danger-color {
    color: var(--el-color-danger);
  }
}

.funnel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.funnel-filter {
  display: flex;
  align-items: center;
}

.funnel-stage {
  text-align: center;
  padding: 12px 8px;

  .funnel-stage-title {
    font-size: 13px;
    color: var(--el-text-color-secondary);
    margin-bottom: 8px;
  }

  .funnel-stage-value {
    font-size: 28px;
    font-weight: 700;
    margin-bottom: 4px;
  }

  .funnel-stage-rate {
    font-size: 12px;
    color: var(--el-color-success);
    margin-bottom: 8px;
  }

  .funnel-stage-bar {
    height: 6px;
    border-radius: 3px;
    transition: width 0.3s ease;
  }

  .bar-sent {
    background: var(--el-color-primary);
  }

  .bar-delivered {
    background: var(--el-color-success);
  }

  .bar-read {
    background: var(--el-color-warning);
  }

  .bar-clicked {
    background: var(--el-color-danger);
  }
}
</style>
