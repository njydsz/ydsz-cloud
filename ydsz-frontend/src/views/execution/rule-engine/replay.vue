<!--
  @file 执行回放页面
  @description 规则执行回放页面：支持单条 traceId 回放、批量时间范围回放，
               展示历史结果与当前结果差异分析。
               对应路由 /execution/rule-engine/replay。
  @module views/execution/rule-engine
-->
<script setup lang="ts">
/**
 * 执行回放页面
 *
 * 功能区域：
 *  1. 回放模式切换：单条回放 / 批量回放
 *  2. 单条回放：输入 traceId + 执行回放 + 差异分析展示
 *  3. 批量回放：选择时间范围 + 可选规则编码 + 执行批量回放 + 差异报告
 *  4. 影响分析：输入规则编码 + 新表达式 + 预览变更影响
 */
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, VideoPlay, DataAnalysis, TrendCharts } from '@element-plus/icons-vue'
import * as ruleApi from '@/api/rule-engine'
import type {
  ReplayResult,
  BatchReplayResult,
  ImpactPreviewResult,
} from '@/api/rule-engine'
import { logger } from '@/utils/logger'

// ==================== 响应式状态 ====================

/** 回放模式 */
const mode = ref<'single' | 'batch' | 'impact'>('single')

// --- 单条回放 ---
const singleTraceId = ref('')
const singleResult = ref<ReplayResult | null>(null)
const singleLoading = ref(false)

// --- 批量回放 ---
const batchForm = reactive({
  dateRange: null as [string, string] | null,
  ruleCode: '',
  limit: 100,
})
const batchResult = ref<BatchReplayResult | null>(null)
const batchLoading = ref(false)

// --- 影响分析 ---
const impactForm = reactive({
  ruleCode: '',
  conditionExpression: '',
  severityExpression: '',
  defaultSeverity: 'YELLOW',
  limit: 1000,
})
const impactResult = ref<ImpactPreviewResult | null>(null)
const impactLoading = ref(false)

// ==================== 计算属性 ====================

/** 差异类型标签映射 */
const diffTypeMap: Record<string, { label: string; type: string }> = {
  ADDED: { label: '新增触发', type: 'danger' },
  REMOVED: { label: '减少触发', type: 'success' },
  SEVERITY_CHANGED: { label: '严重度变化', type: 'warning' },
  UNCHANGED: { label: '不变', type: 'info' },
  triggered_to_not: { label: '触发→未触发', type: 'success' },
  not_to_triggered: { label: '未触发→触发', type: 'danger' },
  severity_changed: { label: '严重度变化', type: 'warning' },
  consistent: { label: '一致', type: 'info' },
  added: { label: '新增触发', type: 'danger' },
  removed: { label: '减少触发', type: 'success' },
  severityChanged: { label: '严重度变化', type: 'warning' },
  unchanged: { label: '不变', type: 'info' },
}

/** 严重度选项 */
const severityOptions = [
  { label: '红色 RED', value: 'RED' },
  { label: '黄色 YELLOW', value: 'YELLOW' },
  { label: '通知 NORMAL', value: 'NORMAL' },
]

// ==================== 方法 ====================

/** 单条回放 */
async function handleSingleReplay() {
  if (!singleTraceId.value.trim()) {
    ElMessage.warning('请输入 traceId')
    return
  }
  singleLoading.value = true
  try {
    const res = await ruleApi.replayTrace(singleTraceId.value.trim())
    singleResult.value = res
    if (res.errorMessage) {
      ElMessage.error(res.errorMessage)
    } else {
      const diff = res.diff
      if (diff) {
        ElMessage.success(diff.summary)
      } else {
        ElMessage.success('回放完成')
      }
    }
  } catch (e: any) {
    logger.error('单条回放失败', e)
    ElMessage.error('回放失败: ' + (e.message || '未知错误'))
  } finally {
    singleLoading.value = false
  }
}

/** 批量回放 */
async function handleBatchReplay() {
  if (!batchForm.dateRange) {
    ElMessage.warning('请选择时间范围')
    return
  }
  batchLoading.value = true
  try {
    const res = await ruleApi.batchReplayTraces({
      startTime: batchForm.dateRange[0],
      endTime: batchForm.dateRange[1],
      ruleCode: batchForm.ruleCode.trim() || undefined,
      limit: batchForm.limit,
    })
    batchResult.value = res
    ElMessage.success(res.summary)
  } catch (e: any) {
    logger.error('批量回放失败', e)
    ElMessage.error('批量回放失败: ' + (e.message || '未知错误'))
  } finally {
    batchLoading.value = false
  }
}

/** 影响分析 */
async function handleImpactPreview() {
  if (!impactForm.ruleCode.trim()) {
    ElMessage.warning('请输入规则编码')
    return
  }
  if (!impactForm.conditionExpression.trim()) {
    ElMessage.warning('请输入条件表达式')
    return
  }
  impactLoading.value = true
  try {
    const res = await ruleApi.impactPreview(impactForm.ruleCode.trim(), {
      conditionExpression: impactForm.conditionExpression,
      severityExpression: impactForm.severityExpression || undefined,
      defaultSeverity: impactForm.defaultSeverity || undefined,
      limit: impactForm.limit,
    })
    impactResult.value = res
    ElMessage.success(res.summary)
  } catch (e: any) {
    logger.error('影响分析失败', e)
    ElMessage.error('影响分析失败: ' + (e.message || '未知错误'))
  } finally {
    impactLoading.value = false
  }
}

/** 获取差异类型标签 */
function getDiffTag(diffType: string) {
  return diffTypeMap[diffType] || { label: diffType, type: 'info' }
}

/** 格式化时间 */
function formatTime(time?: string) {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}
</script>

<template>
  <div class="replay-page">
    <!-- 页头 -->
    <el-page-header @back="$router.push('/execution/rule-engine')" class="mb-4">
      <template #content>
        <span class="page-title">{{ $t('execution.ruleEngine.executionReplay') }}</span>
      </template>
    </el-page-header>

    <!-- 模式切换 -->
    <el-tabs v-model="mode" class="replay-tabs">
      <!-- 单条回放 -->
      <el-tab-pane label="单条回放" name="single">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>
                <el-icon><VideoPlay /></el-icon>
                基于 traceId 回放
              </span>
              <el-button
                :icon="Search"
                type="primary"
                :loading="singleLoading"
                @click="handleSingleReplay"
              >
                执行回放
              </el-button>
            </div>
          </template>
          <el-form :inline="true">
            <el-form-item label="Trace ID">
              <el-input
                v-model="singleTraceId"
                placeholder="输入历史执行追踪 ID"
                clearable
                style="width: 400px"
                @keyup.enter="handleSingleReplay"
              />
            </el-form-item>
          </el-form>

          <!-- 回放结果 -->
          <template v-if="singleResult">
            <el-alert
              v-if="singleResult.errorMessage"
              :title="singleResult.errorMessage"
              type="error"
              :closable="false"
              class="mb-4"
            />

            <template v-else>
              <!-- 差异概览 -->
              <el-row :gutter="16" class="mb-4">
                <el-col :span="8">
                  <el-statistic title="新增触发" :value="singleResult.diff?.added?.length || 0">
                    <template #suffix>
                      <el-text type="danger" size="small">条</el-text>
                    </template>
                  </el-statistic>
                </el-col>
                <el-col :span="8">
                  <el-statistic title="移除触发" :value="singleResult.diff?.removed?.length || 0">
                    <template #suffix>
                      <el-text type="success" size="small">条</el-text>
                    </template>
                  </el-statistic>
                </el-col>
                <el-col :span="8">
                  <el-statistic title="保持不变" :value="singleResult.diff?.unchanged?.length || 0">
                    <template #suffix>
                      <el-text type="info" size="small">条</el-text>
                    </template>
                  </el-statistic>
                </el-col>
              </el-row>

              <el-alert
                :title="singleResult.diff?.summary || ''"
                type="info"
                :closable="false"
                class="mb-4"
              />

              <!-- 事实快照 -->
              <el-collapse>
                <el-collapse-item title="事实快照（Facts Snapshot）" name="facts">
                  <pre class="json-display">{{ JSON.stringify(singleResult.factsSnapshot, null, 2) }}</pre>
                </el-collapse-item>
                <el-collapse-item title="历史执行轨迹" name="historical">
                  <el-table
                    :data="singleResult.historicalTraces || []"
                    size="small"
                    stripe
                    border
                  >
                    <el-table-column prop="ruleCode" label="规则编码" width="180" />
                    <el-table-column label="历史触发" width="90" align="center">
                      <template #default="{ row }">
                        <el-tag :type="row.triggered ? 'danger' : 'info'" size="small">
                          {{ row.triggered ? '是' : '否' }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="severity" label="严重度" width="90" />
                    <el-table-column prop="elapsedMs" label="耗时(ms)" width="100" />
                    <el-table-column label="时间" width="170">
                      <template #default="{ row }">
                        {{ formatTime(row.createdAt || row.timestamp) }}
                      </template>
                    </el-table-column>
                  </el-table>
                </el-collapse-item>
                <el-collapse-item title="当前评估结果" name="current">
                  <el-table
                    :data="singleResult.currentResults || []"
                    size="small"
                    stripe
                    border
                  >
                    <el-table-column prop="ruleCode" label="规则编码" width="180" />
                    <el-table-column label="当前触发" width="90" align="center">
                      <template #default="{ row }">
                        <el-tag :type="row.triggered ? 'danger' : 'info'" size="small">
                          {{ row.triggered ? '是' : '否' }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="severity" label="严重度" width="90" />
                    <el-table-column prop="title" label="标题" show-overflow-tooltip />
                    <el-table-column prop="description" label="描述" show-overflow-tooltip />
                  </el-table>
                </el-collapse-item>
                <el-collapse-item title="差异详情" name="diff">
                  <el-row :gutter="16">
                    <el-col :span="8">
                      <h5 style="color: #f56c6c">新增触发（Added）</h5>
                      <el-tag
                        v-for="code in singleResult.diff?.added || []"
                        :key="code"
                        type="danger"
                        size="small"
                        class="diff-tag"
                      >
                        {{ code }}
                      </el-tag>
                      <el-empty v-if="!(singleResult.diff?.added?.length)" description="无" :image-size="40" />
                    </el-col>
                    <el-col :span="8">
                      <h5 style="color: #67c23a">移除触发（Removed）</h5>
                      <el-tag
                        v-for="code in singleResult.diff?.removed || []"
                        :key="code"
                        type="success"
                        size="small"
                        class="diff-tag"
                      >
                        {{ code }}
                      </el-tag>
                      <el-empty v-if="!(singleResult.diff?.removed?.length)" description="无" :image-size="40" />
                    </el-col>
                    <el-col :span="8">
                      <h5 style="color: #909399">保持不变（Unchanged）</h5>
                      <el-tag
                        v-for="code in singleResult.diff?.unchanged || []"
                        :key="code"
                        type="info"
                        size="small"
                        class="diff-tag"
                      >
                        {{ code }}
                      </el-tag>
                      <el-empty v-if="!(singleResult.diff?.unchanged?.length)" description="无" :image-size="40" />
                    </el-col>
                  </el-row>
                </el-collapse-item>
              </el-collapse>
            </template>
          </template>
          <el-empty v-else description="输入 traceId 并点击「执行回放」" />
        </el-card>
      </el-tab-pane>

      <!-- 批量回放 -->
      <el-tab-pane label="批量回放" name="batch">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>
                <el-icon><DataAnalysis /></el-icon>
                按时间范围批量回放
              </span>
              <el-button
                :icon="Search"
                type="primary"
                :loading="batchLoading"
                @click="handleBatchReplay"
              >
                执行批量回放
              </el-button>
            </div>
          </template>
          <el-form :inline="true" label-width="100px">
            <el-form-item label="时间范围">
              <el-date-picker
                v-model="batchForm.dateRange"
                type="datetimerange"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                value-format="YYYY-MM-DDTHH:mm:ss"
                style="width: 380px"
              />
            </el-form-item>
            <el-form-item label="规则编码">
              <el-input
                v-model="batchForm.ruleCode"
                placeholder="为空表示全部规则"
                clearable
                style="width: 200px"
              />
            </el-form-item>
            <el-form-item label="最大条数">
              <el-input-number
                v-model="batchForm.limit"
                :min="1"
                :max="1000"
                :step="50"
                style="width: 120px"
              />
            </el-form-item>
          </el-form>

          <!-- 批量回放结果 -->
          <template v-if="batchResult">
            <!-- 统计概览 -->
            <el-row :gutter="16" class="mb-4">
              <el-col :span="6">
                <el-statistic title="总回放数" :value="batchResult.totalReplayed">
                  <template #suffix><el-text size="small">条</el-text></template>
                </el-statistic>
              </el-col>
              <el-col :span="6">
                <el-statistic title="一致数" :value="batchResult.consistentCount">
                  <template #suffix><el-text type="success" size="small">条</el-text></template>
                </el-statistic>
              </el-col>
              <el-col :span="6">
                <el-statistic title="差异数" :value="batchResult.diffCount">
                  <template #suffix><el-text type="danger" size="small">条</el-text></template>
                </el-statistic>
              </el-col>
              <el-col :span="6">
                <el-statistic title="跳过数" :value="batchResult.skippedCount || 0">
                  <template #suffix><el-text type="info" size="small">条</el-text></template>
                </el-statistic>
              </el-col>
            </el-row>

            <el-alert :title="batchResult.summary" type="info" :closable="false" class="mb-4" />

            <!-- 差异列表 -->
            <el-table
              :data="batchResult.diffs"
              size="small"
              stripe
              border
              max-height="500"
            >
              <el-table-column prop="traceId" label="Trace ID" width="200" show-overflow-tooltip />
              <el-table-column prop="ruleCode" label="规则编码" width="180" />
              <el-table-column prop="ruleName" label="规则名称" width="180" show-overflow-tooltip />
              <el-table-column label="历史触发" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.historicalTriggered ? 'danger' : 'info'" size="small">
                    {{ row.historicalTriggered ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="当前触发" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.currentTriggered ? 'danger' : 'info'" size="small">
                    {{ row.currentTriggered ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="historicalSeverity" label="历史严重度" width="100" />
              <el-table-column prop="currentSeverity" label="当前严重度" width="100" />
              <el-table-column label="差异类型" width="120" align="center">
                <template #default="{ row }">
                  <el-tag :type="getDiffTag(row.diffType).type as any" size="small">
                    {{ getDiffTag(row.diffType).label }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </template>
          <el-empty v-else description="选择时间范围并点击「执行批量回放」" />
        </el-card>
      </el-tab-pane>

      <!-- 影响分析 -->
      <el-tab-pane label="影响分析" name="impact">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>
                <el-icon><TrendCharts /></el-icon>
                规则变更影响预览
              </span>
              <el-button
                :icon="Search"
                type="primary"
                :loading="impactLoading"
                @click="handleImpactPreview"
              >
                执行影响分析
              </el-button>
            </div>
          </template>
          <el-form label-width="120px">
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="规则编码" required>
                  <el-input
                    v-model="impactForm.ruleCode"
                    placeholder="如 EVM_RED_ALERT"
                    clearable
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="分析条数">
                  <el-input-number
                    v-model="impactForm.limit"
                    :min="1"
                    :max="5000"
                    :step="100"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="新条件表达式" required>
              <el-input
                v-model="impactForm.conditionExpression"
                type="textarea"
                :rows="2"
                placeholder="如: evmRedCount >= 5"
              />
            </el-form-item>
            <el-form-item label="新严重度表达式">
              <el-input
                v-model="impactForm.severityExpression"
                type="textarea"
                :rows="2"
                placeholder="如: evmRedCount >= 10 ? 'RED' : 'YELLOW'"
              />
            </el-form-item>
            <el-form-item label="默认严重度">
              <el-select v-model="impactForm.defaultSeverity" style="width: 200px">
                <el-option
                  v-for="opt in severityOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-form>

          <!-- 影响分析结果 -->
          <template v-if="impactResult">
            <!-- 统计概览 -->
            <el-row :gutter="16" class="mb-4">
              <el-col :span="6">
                <el-statistic title="总分析数" :value="impactResult.totalTraces">
                  <template #suffix><el-text size="small">条</el-text></template>
                </el-statistic>
              </el-col>
              <el-col :span="6">
                <el-statistic title="历史触发" :value="impactResult.historicalTriggeredCount">
                  <template #suffix><el-text type="warning" size="small">条</el-text></template>
                </el-statistic>
              </el-col>
              <el-col :span="6">
                <el-statistic title="新表达式触发" :value="impactResult.newTriggeredCount">
                  <template #suffix><el-text type="danger" size="small">条</el-text></template>
                </el-statistic>
              </el-col>
              <el-col :span="6">
                <el-statistic title="新增触发" :value="impactResult.addedTriggeredCount">
                  <template #suffix><el-text type="danger" size="small">条</el-text></template>
                </el-statistic>
              </el-col>
            </el-row>

            <el-alert :title="impactResult.summary" type="info" :closable="false" class="mb-4" />

            <!-- 受影响的 trace 列表 -->
            <h4 class="section-title">受影响的执行记录（非 unchanged）</h4>
            <el-table
              :data="impactResult.affectedTraces"
              size="small"
              stripe
              border
              max-height="500"
            >
              <el-table-column prop="traceId" label="Trace ID" width="200" show-overflow-tooltip />
              <el-table-column label="历史触发" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.historicalTriggered ? 'danger' : 'info'" size="small">
                    {{ row.historicalTriggered ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="新表达式触发" width="110" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.newTriggered ? 'danger' : 'info'" size="small">
                    {{ row.newTriggered ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="historicalSeverity" label="历史严重度" width="110" />
              <el-table-column prop="newSeverity" label="新严重度" width="100" />
              <el-table-column label="影响类型" width="120" align="center">
                <template #default="{ row }">
                  <el-tag :type="getDiffTag(row.impactType).type as any" size="small">
                    {{ getDiffTag(row.impactType).label }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="时间" width="170">
                <template #default="{ row }">
                  {{ formatTime(row.createdAt) }}
                </template>
              </el-table-column>
            </el-table>
            <el-empty
              v-if="impactResult.affectedTraces.length === 0"
              description="无受影响的执行记录"
            />
          </template>
          <el-empty v-else description="填写规则编码和新表达式，点击「执行影响分析」" />
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.replay-page {
  padding: 16px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  margin: 16px 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.diff-tag {
  margin: 2px 4px;
  font-family: monospace;
}

.json-display {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  max-height: 400px;
  overflow: auto;
}

.replay-tabs :deep(.el-tabs__content) {
  padding-top: 16px;
}
</style>
