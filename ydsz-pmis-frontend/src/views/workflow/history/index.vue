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
      ElMessage.warning('加载归档配置失败')
    }
  } catch (e) {
    ElMessage.error('加载归档配置失败：' + (e as Error).message)
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
        ElMessage.error('归档失败：' + (res.data.data.error || '未知错误'))
      } else {
        ElMessage.success(
          `归档完成：归档 ${res.data.data.archived ?? 0} 条，跳过 ${res.data.data.missing ?? 0} 条，异常 ${res.data.data.errors ?? 0} 条`,
        )
      }
    } else {
      ElMessage.error(res.data?.message || '归档失败')
    }
  } catch (e) {
    ElMessage.error('归档请求失败：' + (e as Error).message)
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
      `确认立即清理归档表中超过 ${purgeDays} 天的冷数据？此操作不可恢复，将物理删除归档实例与变量行。`,
      '高危操作确认',
      {
        confirmButtonText: '确认清理',
        cancelButtonText: '取消',
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
        '当前 purgeEnabled=false（配置未开启自动清理）。手动接口仍可强制执行，是否继续？',
        '配置未开启',
        {
          confirmButtonText: '强制执行',
          cancelButtonText: '取消',
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
        ElMessage.warning('清理已跳过：' + (res.data.data.reason || 'purgeEnabled=false'))
      } else if (res.data.data.ok === false) {
        ElMessage.error('清理失败：' + (res.data.data.error || '未知错误'))
      } else {
        ElMessage.success(
          `清理完成：删除归档实例 ${res.data.data.purgedInstances ?? 0} 条，变量行 ${res.data.data.purgedVariables ?? 0} 行`,
        )
      }
    } else {
      ElMessage.error(res.data?.message || '清理失败')
    }
  } catch (e) {
    ElMessage.error('清理请求失败：' + (e as Error).message)
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
      <h2>流程历史数据归档</h2>
      <p class="page-header__sub">
        管理流程实例的冷热数据分离策略，支持手动触发归档与清理，避免主表数据膨胀影响查询性能。
      </p>
    </div>

    <!-- 归档策略展示 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <span>当前归档策略</span>
          <el-button size="small" text @click="loadConfig">刷新</el-button>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="自动归档">
          <el-tag :type="config.archiveEnabled ? 'success' : 'info'" size="small">
            {{ config.archiveEnabled ? '已启用' : '已停用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="归档阈值天数">
          {{ config.retentionDays }} 天
        </el-descriptions-item>
        <el-descriptions-item label="单次批量大小">
          {{ config.batchSize }} 条
        </el-descriptions-item>
        <el-descriptions-item label="单次最大耗时">
          {{ config.maxProcessMs }} ms
        </el-descriptions-item>
        <el-descriptions-item label="Cron 表达式">
          <code>{{ config.cronExpression }}</code>
        </el-descriptions-item>
        <el-descriptions-item label="自动清理">
          <el-tag :type="config.purgeEnabled ? 'success' : 'info'" size="small">
            {{ config.purgeEnabled ? '已启用' : '已停用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="清理阈值天数">
          {{ config.purgeDays }} 天（约 {{ Math.round(config.purgeDays / 365 * 10) / 10 }} 年）
        </el-descriptions-item>
      </el-descriptions>
      <div class="config-tip">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="配置说明"
          description="归档策略通过 application.yml 的 pmis.flow.history.* 配置，支持 Nacos 动态刷新。本页仅提供查看与手动触发能力，不修改配置。"
        />
      </div>
    </el-card>

    <!-- 手动归档 -->
    <el-card shadow="never" class="section">
      <template #header>
        <span>手动归档</span>
      </template>
      <p class="section-desc">
        立即触发一次归档。参数留空则使用上方配置的默认值。适用于磁盘空间告急、上线前验证归档逻辑等场景。
      </p>
      <el-form :model="archiveForm" label-width="140px" inline>
        <el-form-item label="归档阈值天数">
          <el-input-number
            v-model="archiveForm.retentionDays"
            :min="1"
            :max="3650"
            placeholder="默认 30"
            controls-position="right"
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="单次批量大小">
          <el-input-number
            v-model="archiveForm.batchSize"
            :min="1"
            :max="10000"
            placeholder="默认 100"
            controls-position="right"
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="单次最大耗时(ms)">
          <el-input-number
            v-model="archiveForm.maxProcessMs"
            :min="1000"
            :max="3600000"
            :step="1000"
            placeholder="默认 30000"
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
            立即归档
          </el-button>
          <el-button @click="resetArchiveForm">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 归档结果 -->
      <div v-if="archiveResult" class="result-box">
        <el-descriptions :column="3" border size="small" title="归档结果">
          <el-descriptions-item label="状态">
            <el-tag :type="archiveResult.ok === false ? 'danger' : 'success'" size="small">
              {{ archiveResult.ok === false ? '失败' : '成功' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="候选总数">
            {{ archiveResult.total ?? 0 }}
          </el-descriptions-item>
          <el-descriptions-item label="已归档">
            <span class="num-success">{{ archiveResult.archived ?? 0 }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="跳过(任务未终态)">
            <span class="num-warning">{{ archiveResult.missing ?? 0 }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="异常">
            <span class="num-danger">{{ archiveResult.errors ?? 0 }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">
            {{ archiveResult.costMs ?? 0 }} ms
          </el-descriptions-item>
          <el-descriptions-item v-if="archiveResult.error" label="错误信息" :span="3">
            <span class="num-danger">{{ archiveResult.error }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>

    <!-- 手动清理 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <span>手动清理（Purge）</span>
          <el-tag type="danger" size="small" effect="dark">高危</el-tag>
        </div>
      </template>
      <p class="section-desc">
        物理删除归档表中超过阈值天数的冷数据，回收存储空间。即使 purgeEnabled=false，本接口仍可强制执行。
      </p>
      <el-form :model="purgeForm" label-width="140px" inline>
        <el-form-item label="清理阈值天数">
          <el-input-number
            v-model="purgeForm.purgeDays"
            :min="1"
            :max="36500"
            placeholder="默认 1825"
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
            立即清理
          </el-button>
          <el-button @click="resetPurgeForm">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 清理结果 -->
      <div v-if="purgeResult" class="result-box">
        <el-descriptions :column="3" border size="small" title="清理结果">
          <el-descriptions-item label="状态">
            <el-tag
              v-if="purgeResult.skipped"
              type="warning"
              size="small"
            >跳过</el-tag>
            <el-tag
              v-else
              :type="purgeResult.ok === false ? 'danger' : 'success'"
              size="small"
            >
              {{ purgeResult.ok === false ? '失败' : '成功' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="清理阈值">
            {{ purgeResult.purgeDays ?? 0 }} 天
          </el-descriptions-item>
          <el-descriptions-item label="已清理实例">
            <span class="num-danger">{{ purgeResult.purgedInstances ?? 0 }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="已清理变量行">
            <span class="num-danger">{{ purgeResult.purgedVariables ?? 0 }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">
            {{ purgeResult.costMs ?? 0 }} ms
          </el-descriptions-item>
          <el-descriptions-item v-if="purgeResult.reason" label="跳过原因" :span="3">
            {{ purgeResult.reason }}
          </el-descriptions-item>
          <el-descriptions-item v-if="purgeResult.error" label="错误信息" :span="3">
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
