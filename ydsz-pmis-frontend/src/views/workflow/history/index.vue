<!--
  @fileoverview 流程历史数据归档管理页
  @description
    运维视角的历史数据管理：
      1. 归档策略展示（archiveEnabled / retentionDays / batchSize / cron / purgeEnabled 等）；
      2. 手动归档（可临时覆盖阈值，立即触发一次归档）；
      3. 手动清理（高危操作，二次确认 + purgeEnabled=false 时强提示）；
      4. 操作结果摘要（archived / missing / errors / purgedInstances / purgedVariables / costMs）。
    配置通过 application.yml + nacos 下发，本页仅"查看 + 触发"。
    配套自研工作流 v2 引擎，PC 端专用。
  @module views/workflow/history
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * @file 流程历史数据归档管理页
 * @module views/workflow/history
 * @description P2-8: 运维视角的历史数据管理：
 *   1. 归档策略展示：从后端读取当前生效的配置（archiveEnabled/retentionDays/batchSize/...）
 *   2. 手动归档：可临时覆盖 retentionDays/batchSize/maxProcessMs，立即触发一次归档
 *   3. 手动清理：可临时覆盖 purgeDays，立即清理归档表中的冷数据
 *   4. 操作结果摘要：archived/missing/errors/purgedInstances/purgedVariables/costMs
 *
 *   设计要点：
 *   - purge 为高危操作（物理删除归档数据），二次确认 + purgeEnabled=false 时强提示
 *   - 操作期间按钮 loading，避免重复提交
 *   - 不支持修改配置（配置走 application.yml + nacos），本页仅"查看 + 触发"
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  getHistoryConfig,
  triggerArchive,
  purgeHistory,
} from '@/api/workflow'
import type {
  FlowHistoryConfig,
  FlowHistoryArchiveResult,
  FlowHistoryPurgeResult,
} from '@/api/workflow'

const { t } = useI18n()

// ==================== 配置展示 ====================
const config = ref<FlowHistoryConfig>({
  archiveEnabled: true,
  retentionDays: 30,
  batchSize: 100,
  maxProcessMs: 30000,
  cronExpression: '0 0 3 * * ?',
  purgeEnabled: false,
  purgeDays: 1825,
})
const configLoading = ref(false)

async function loadConfig() {
  configLoading.value = true
  try {
    const res = await getHistoryConfig()
    if (res.data?.code === 0 && res.data.data) {
      config.value = res.data.data
    } else {
      ElMessage.warning(t('workflow.history.msg.loadConfigFailed'))
    }
  } catch (e) {
    ElMessage.error(t('workflow.history.msg.loadConfigFailedWithMsg', { reason: (e as Error).message }))
  } finally {
    configLoading.value = false
  }
}

// ==================== 手动归档 ====================
const archiveForm = reactive({
  retentionDays: undefined as number | undefined,
  batchSize: undefined as number | undefined,
  maxProcessMs: undefined as number | undefined,
})
const archiveLoading = ref(false)
const archiveResult = ref<FlowHistoryArchiveResult | null>(null)

async function doArchive() {
  // 至少填写一个覆盖项才提示，否则使用默认值直接执行
  archiveLoading.value = true
  archiveResult.value = null
  try {
    const res = await triggerArchive({
      retentionDays: archiveForm.retentionDays,
      batchSize: archiveForm.batchSize,
      maxProcessMs: archiveForm.maxProcessMs,
    })
    if (res.data?.code === 0 && res.data.data) {
      archiveResult.value = res.data.data
      if (res.data.data.ok === false) {
        ElMessage.error(t('workflow.history.msg.archiveFailedWithMsg', { reason: res.data.data.error || t('workflow.history.msg.unknownError') }))
      } else {
        ElMessage.success(
          t('workflow.history.msg.archiveComplete', {
            archived: res.data.data.archived ?? 0,
            missing: res.data.data.missing ?? 0,
            errors: res.data.data.errors ?? 0,
          }),
        )
      }
    } else {
      ElMessage.error(res.data?.message || t('workflow.history.msg.archiveFailed'))
    }
  } catch (e) {
    ElMessage.error(t('workflow.history.msg.archiveRequestFailedWithMsg', { reason: (e as Error).message }))
  } finally {
    archiveLoading.value = false
  }
}

function resetArchiveForm() {
  archiveForm.retentionDays = undefined
  archiveForm.batchSize = undefined
  archiveForm.maxProcessMs = undefined
  archiveResult.value = null
}

// ==================== 手动清理 ====================
const purgeForm = reactive({
  purgeDays: undefined as number | undefined,
})
const purgeLoading = ref(false)
const purgeResult = ref<FlowHistoryPurgeResult | null>(null)

async function doPurge() {
  // 高危操作二次确认
  const purgeDays = purgeForm.purgeDays ?? config.value.purgeDays
  try {
    await ElMessageBox.confirm(
      t('workflow.history.msg.purgeConfirm', { days: purgeDays }),
      t('workflow.history.msg.purgeConfirmTitle'),
      {
        confirmButtonText: t('workflow.history.msg.purgeConfirmBtn'),
        cancelButtonText: t('workflow.history.msg.cancel'),
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    // 用户取消
    return
  }

  if (!config.value.purgeEnabled) {
    try {
      await ElMessageBox.confirm(
        t('workflow.history.msg.purgeForceConfirm'),
        t('workflow.history.msg.purgeForceConfirmTitle'),
        {
          confirmButtonText: t('workflow.history.msg.purgeForceBtn'),
          cancelButtonText: t('workflow.history.msg.cancel'),
          type: 'warning',
        },
      )
    } catch {
      return
    }
  }

  purgeLoading.value = true
  purgeResult.value = null
  try {
    const res = await purgeHistory(purgeForm.purgeDays)
    if (res.data?.code === 0 && res.data.data) {
      purgeResult.value = res.data.data
      if (res.data.data.skipped) {
        ElMessage.warning(t('workflow.history.msg.purgeSkippedWithMsg', { reason: res.data.data.reason || t('workflow.history.msg.purgeDisabledHint') }))
      } else if (res.data.data.ok === false) {
        ElMessage.error(t('workflow.history.msg.purgeFailedWithMsg', { reason: res.data.data.error || t('workflow.history.msg.unknownError') }))
      } else {
        ElMessage.success(
          t('workflow.history.msg.purgeComplete', {
            instances: res.data.data.purgedInstances ?? 0,
            variables: res.data.data.purgedVariables ?? 0,
          }),
        )
      }
    } else {
      ElMessage.error(res.data?.message || t('workflow.history.msg.purgeFailed'))
    }
  } catch (e) {
    ElMessage.error(t('workflow.history.msg.purgeRequestFailedWithMsg', { reason: (e as Error).message }))
  } finally {
    purgeLoading.value = false
  }
}

function resetPurgeForm() {
  purgeForm.purgeDays = undefined
  purgeResult.value = null
}

// ==================== 生命周期 ====================
onMounted(() => {
  loadConfig()
})
</script>

<template>
  <div class="history-archive-page" v-loading="configLoading">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>{{ t('workflow.history.title') }}</h2>
      <p class="page-header__sub">
        {{ t('workflow.history.subtitle') }}
      </p>
    </div>

    <!-- 归档策略展示 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <span>{{ t('workflow.history.strategy.current') }}</span>
          <el-button size="small" text @click="loadConfig">{{ t('workflow.history.strategy.refresh') }}</el-button>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item :label="t('workflow.history.strategy.archiveEnabled')">
          <el-tag :type="config.archiveEnabled ? 'success' : 'info'" size="small">
            {{ config.archiveEnabled ? t('workflow.history.status.enabled') : t('workflow.history.status.disabled') }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('workflow.history.strategy.retentionDays')">
          {{ config.retentionDays }} {{ t('workflow.history.units.days') }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('workflow.history.strategy.batchSize')">
          {{ config.batchSize }} {{ t('workflow.history.units.records') }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('workflow.history.strategy.maxProcessMs')">
          {{ config.maxProcessMs }} {{ t('workflow.history.units.ms') }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('workflow.history.strategy.cronExpression')">
          <code>{{ config.cronExpression }}</code>
        </el-descriptions-item>
        <el-descriptions-item :label="t('workflow.history.strategy.purgeEnabled')">
          <el-tag :type="config.purgeEnabled ? 'success' : 'info'" size="small">
            {{ config.purgeEnabled ? t('workflow.history.status.enabled') : t('workflow.history.status.disabled') }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('workflow.history.strategy.purgeDays')">
          {{ config.purgeDays }} {{ t('workflow.history.units.days') }}（{{ t('workflow.history.units.about') }} {{ Math.round(config.purgeDays / 365 * 10) / 10 }} {{ t('workflow.history.units.years') }}）
        </el-descriptions-item>
      </el-descriptions>
      <div class="config-tip">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          :title="t('workflow.history.strategy.configTipTitle')"
          :description="t('workflow.history.strategy.configTipDesc')"
        />
      </div>
    </el-card>

    <!-- 手动归档 -->
    <el-card shadow="never" class="section">
      <template #header>
        <span>{{ t('workflow.history.archive.title') }}</span>
      </template>
      <p class="section-desc">
        {{ t('workflow.history.archive.sectionDesc') }}
      </p>
      <el-form :model="archiveForm" label-width="140px" inline>
        <el-form-item :label="t('workflow.history.archive.retentionDays')">
          <el-input-number
            v-model="archiveForm.retentionDays"
            :min="1"
            :max="3650"
            :placeholder="t('workflow.history.archive.retentionPlaceholder')"
            controls-position="right"
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item :label="t('workflow.history.archive.batchSize')">
          <el-input-number
            v-model="archiveForm.batchSize"
            :min="1"
            :max="10000"
            :placeholder="t('workflow.history.archive.batchPlaceholder')"
            controls-position="right"
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item :label="t('workflow.history.archive.maxProcessMs')">
          <el-input-number
            v-model="archiveForm.maxProcessMs"
            :min="1000"
            :max="3600000"
            :step="1000"
            :placeholder="t('workflow.history.archive.maxProcessMsPlaceholder')"
            controls-position="right"
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="archiveLoading"
            @click="doArchive"
          >
            {{ t('workflow.history.archive.trigger') }}
          </el-button>
          <el-button @click="resetArchiveForm">{{ t('workflow.history.archive.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <!-- 归档结果 -->
      <div v-if="archiveResult" class="result-box">
        <el-descriptions :column="3" border size="small" :title="t('workflow.history.result.archiveTitle')">
          <el-descriptions-item :label="t('workflow.history.result.status')">
            <el-tag :type="archiveResult.ok === false ? 'danger' : 'success'" size="small">
              {{ archiveResult.ok === false ? t('workflow.history.status.failed') : t('workflow.history.status.success') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('workflow.history.result.total')">
            {{ archiveResult.total ?? 0 }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('workflow.history.result.archived')">
            <span class="num-success">{{ archiveResult.archived ?? 0 }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('workflow.history.result.missing')">
            <span class="num-warning">{{ archiveResult.missing ?? 0 }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('workflow.history.result.errors')">
            <span class="num-danger">{{ archiveResult.errors ?? 0 }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('workflow.history.result.costMs')">
            {{ archiveResult.costMs ?? 0 }} {{ t('workflow.history.units.ms') }}
          </el-descriptions-item>
          <el-descriptions-item v-if="archiveResult.error" :label="t('workflow.history.result.errorMsg')" :span="3">
            <span class="num-danger">{{ archiveResult.error }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>

    <!-- 手动清理 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <span>{{ t('workflow.history.purge.title') }}</span>
          <el-tag type="danger" size="small" effect="dark">{{ t('workflow.history.purge.highRisk') }}</el-tag>
        </div>
      </template>
      <p class="section-desc">
        {{ t('workflow.history.purge.sectionDesc') }}
      </p>
      <el-form :model="purgeForm" label-width="140px" inline>
        <el-form-item :label="t('workflow.history.purge.purgeDays')">
          <el-input-number
            v-model="purgeForm.purgeDays"
            :min="1"
            :max="36500"
            :placeholder="t('workflow.history.purge.purgeDaysPlaceholder')"
            controls-position="right"
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="danger"
            :loading="purgeLoading"
            @click="doPurge"
          >
            {{ t('workflow.history.purge.trigger') }}
          </el-button>
          <el-button @click="resetPurgeForm">{{ t('workflow.history.purge.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <!-- 清理结果 -->
      <div v-if="purgeResult" class="result-box">
        <el-descriptions :column="3" border size="small" :title="t('workflow.history.result.purgeTitle')">
          <el-descriptions-item :label="t('workflow.history.result.status')">
            <el-tag
              v-if="purgeResult.skipped"
              type="warning"
              size="small"
            >{{ t('workflow.history.status.skipped') }}</el-tag>
            <el-tag
              v-else
              :type="purgeResult.ok === false ? 'danger' : 'success'"
              size="small"
            >
              {{ purgeResult.ok === false ? t('workflow.history.status.failed') : t('workflow.history.status.success') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('workflow.history.result.purgeDays')">
            {{ purgeResult.purgeDays ?? 0 }} {{ t('workflow.history.units.days') }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('workflow.history.result.purgedInstances')">
            <span class="num-danger">{{ purgeResult.purgedInstances ?? 0 }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('workflow.history.result.purgedVariables')">
            <span class="num-danger">{{ purgeResult.purgedVariables ?? 0 }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('workflow.history.result.costMs')">
            {{ purgeResult.costMs ?? 0 }} {{ t('workflow.history.units.ms') }}
          </el-descriptions-item>
          <el-descriptions-item v-if="purgeResult.reason" :label="t('workflow.history.result.skipReason')" :span="3">
            {{ purgeResult.reason }}
          </el-descriptions-item>
          <el-descriptions-item v-if="purgeResult.error" :label="t('workflow.history.result.errorMsg')" :span="3">
            <span class="num-danger">{{ purgeResult.error }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.history-archive-page {
  padding: 16px;
}

.page-header {
  margin-bottom: 20px;

  h2 {
    margin: 0 0 4px;
    font-size: 20px;
    color: #1e293b;
  }

  &__sub {
    margin: 0;
    color: #64748b;
    font-size: 13px;
  }
}

.section {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.section-desc {
  margin: 0 0 16px;
  color: #64748b;
  font-size: 13px;
}

.config-tip {
  margin-top: 12px;
}

.result-box {
  margin-top: 16px;
}

.num-success {
  color: #52c41a;
  font-weight: 600;
}

.num-warning {
  color: #fa8c16;
  font-weight: 600;
}

.num-danger {
  color: #f5222d;
  font-weight: 600;
}

code {
  padding: 2px 6px;
  background: #f5f7fa;
  border-radius: 3px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  color: #d63384;
}
</style>
