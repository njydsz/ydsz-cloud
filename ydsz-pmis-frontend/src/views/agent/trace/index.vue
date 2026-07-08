<!--
  @fileoverview Agent 链路追踪可视化页面
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { getByTraceId, recentByBiz } from '@/api/agent/trace'
import type { AgentTrace } from '@/api/agent/trace/types'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

const loading = ref(false)
const traces = ref<AgentTrace[]>([])
const searchForm = reactive({
  traceId: '',
  bizType: '',
  bizId: '',
})

async function searchByTraceId() {
  if (!searchForm.traceId) {
    ElMessage.warning(t('agent.trace.messages.traceIdRequired'))
    return
  }
  loading.value = true
  traces.value = []
  try {
    const { data } = await getByTraceId(searchForm.traceId)
    traces.value = (data as AgentTrace[]) || []
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.trace.messages.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function searchByBiz() {
  if (!searchForm.bizType || !searchForm.bizId) {
    ElMessage.warning(t('agent.trace.messages.bizRequired'))
    return
  }
  loading.value = true
  traces.value = []
  try {
    const { data } = await recentByBiz(searchForm.bizType, searchForm.bizId, 50)
    traces.value = (data as AgentTrace[]) || []
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.trace.messages.loadFailed'))
  } finally {
    loading.value = false
  }
}

// 按步骤分组
const groupedTraces = computed(() => {
  const groups: Record<number, AgentTrace[]> = {}
  for (const trace of traces.value) {
    const step = trace.stepIndex || 0
    if (!groups[step]) groups[step] = []
    groups[step].push(trace)
  }
  return Object.entries(groups).sort(([a], [b]) => Number(a) - Number(b))
})

// 总耗时
const totalCost = computed(() => {
  return traces.value.reduce((sum, t) => sum + (t.costMs || 0), 0)
})

// 步骤数
const stepCount = computed(() => {
  const steps = new Set(traces.value.map((t) => t.stepIndex).filter((s) => s && s > 0))
  return steps.size
})

function spanNameColor(spanName: string): string {
  switch (spanName) {
    case 'AGENT_START': return '#409EFF'
    case 'STEP_START': return '#67C23A'
    case 'LLM_THOUGHT': return '#E6A23C'
    case 'LLM_ACTION': return '#F56C6C'
    case 'TOOL_OBSERVATION': return '#909399'
    case 'FINAL_ANSWER': return '#67C23A'
    case 'STEP_END': return '#67C23A'
    case 'AGENT_END': return '#409EFF'
    case 'AGENT_ERROR': return '#F56C6C'
    default: return '#C0C4CC'
  }
}

function spanNameIcon(spanName: string): string {
  switch (spanName) {
    case 'AGENT_START': return 'Promotion'
    case 'STEP_START': return 'Right'
    case 'LLM_THOUGHT': return 'ChatDotRound'
    case 'LLM_ACTION': return 'Tools'
    case 'TOOL_OBSERVATION': return 'View'
    case 'FINAL_ANSWER': return 'CircleCheckFilled'
    case 'STEP_END': return 'Check'
    case 'AGENT_END': return 'CircleClose'
    case 'AGENT_ERROR': return 'CircleCloseFilled'
    default: return 'InfoFilled'
  }
}

function statusTagType(status?: string): 'success' | 'info' | 'warning' | 'danger' {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'danger'
    default: return 'info'
  }
}

function formatJson(str?: string): string {
  if (!str) return ''
  try { return JSON.stringify(JSON.parse(str), null, 2) } catch { return str }
}

onMounted(() => {
  // 初始化
})
</script>

<template>
  <div class="trace-page">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-tabs v-model="searchForm.traceId ? 'trace' : 'biz'">
        <el-tab-pane :label="t('agent.trace.search.byTraceId')" name="trace">
          <div class="search-row">
            <el-input v-model="searchForm.traceId"
              :placeholder="t('agent.trace.search.traceIdPlaceholder')" style="width: 400px" clearable
              @keyup.enter="searchByTraceId" />
            <el-button type="primary" :icon="'Search'" :loading="loading" @click="searchByTraceId">
              {{ t('common.search') }}
            </el-button>
          </div>
        </el-tab-pane>
        <el-tab-pane :label="t('agent.trace.search.byBiz')" name="biz">
          <div class="search-row">
            <el-input v-model="searchForm.bizType" :placeholder="t('agent.trace.search.bizTypePlaceholder')" style="width: 150px" />
            <el-input v-model="searchForm.bizId" :placeholder="t('agent.trace.search.bizIdPlaceholder')" style="width: 200px" />
            <el-button type="primary" :icon="'Search'" :loading="loading" @click="searchByBiz">
              {{ t('common.search') }}
            </el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- KPI -->
    <el-row v-if="traces.length > 0" :gutter="16" style="margin-top: 16px">
      <el-col :span="8">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-title">{{ t('agent.trace.kpi.spans') }}</div>
          <div class="kpi-value">{{ traces.length }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-title">{{ t('agent.trace.kpi.steps') }}</div>
          <div class="kpi-value">{{ stepCount }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-title">{{ t('agent.trace.kpi.totalCost') }}</div>
          <div class="kpi-value">{{ totalCost }}ms</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 链路时间线 -->
    <el-card v-if="traces.length > 0" shadow="never" :header="t('agent.trace.timeline.title')" style="margin-top: 16px">
      <el-timeline>
        <el-timeline-item
          v-for="(trace, idx) in traces"
          :key="idx"
          :type="trace.status === 'FAILED' ? 'danger' : trace.spanName === 'AGENT_END' ? 'success' : 'primary'"
          :timestamp="trace.createdAt"
          size="large"
          placement="top"
        >
          <div class="trace-item">
            <div class="trace-header">
              <el-icon :color="spanNameColor(trace.spanName)">
                <component :is="spanNameIcon(trace.spanName)" />
              </el-icon>
              <el-tag :color="spanNameColor(trace.spanName)" effect="dark" size="small" style="color: #fff; border: none">
                {{ trace.spanName }}
              </el-tag>
              <el-tag v-if="trace.stepIndex && trace.stepIndex > 0" type="info" size="small">
                {{ t('agent.trace.timeline.step') }} {{ trace.stepIndex }}
              </el-tag>
              <el-tag :type="statusTagType(trace.status)" size="small">{{ trace.status }}</el-tag>
              <span v-if="trace.costMs" class="cost-badge">{{ trace.costMs }}ms</span>
            </div>
            <div class="trace-body">
              <div v-if="trace.agentType" class="trace-meta">
                <span class="meta-label">Agent:</span>
                <el-tag size="small">{{ trace.agentType }}</el-tag>
              </div>
              <div v-if="trace.providerTraceId" class="trace-meta">
                <span class="meta-label">Provider Trace:</span>
                <code>{{ trace.providerTraceId }}</code>
              </div>
              <el-collapse v-if="trace.inputData || trace.outputData || trace.errorMsg" style="margin-top: 8px">
                <el-collapse-item v-if="trace.inputData" :title="t('agent.trace.timeline.input')" name="input">
                  <pre class="json-pre">{{ formatJson(trace.inputData) }}</pre>
                </el-collapse-item>
                <el-collapse-item v-if="trace.outputData" :title="t('agent.trace.timeline.output')" name="output">
                  <pre class="json-pre">{{ formatJson(trace.outputData) }}</pre>
                </el-collapse-item>
                <el-collapse-item v-if="trace.errorMsg" :title="t('agent.trace.timeline.error')" name="error">
                  <el-text type="danger">{{ trace.errorMsg }}</el-text>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <!-- 空状态 -->
    <el-empty v-else :description="t('agent.trace.empty')" :image-size="100" style="margin-top: 60px" />
  </div>
</template>

<style lang="scss" scoped>
.trace-page {
  .search-card { margin-bottom: 0; }
  .search-row { display: flex; gap: 8px; align-items: center; }
  .kpi-card {
    text-align: center;
    .kpi-title { font-size: 12px; color: var(--el-text-color-secondary); }
    .kpi-value { font-size: 22px; font-weight: 600; margin-top: 8px; color: var(--el-color-primary); }
  }
  .trace-item {
    .trace-header {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }
    .trace-body {
      margin-top: 8px;
    }
    .trace-meta {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-bottom: 4px;
      .meta-label { font-size: 12px; color: var(--el-text-color-secondary); }
    }
    .cost-badge {
      font-size: 12px;
      color: var(--el-color-warning);
      font-weight: 600;
    }
  }
  .json-pre {
    background: var(--el-fill-color-light);
    padding: 8px;
    border-radius: 4px;
    font-size: 12px;
    max-height: 200px;
    overflow: auto;
    margin: 0;
    white-space: pre-wrap;
    word-break: break-all;
  }
}
</style>
