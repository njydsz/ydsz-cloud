<!--
  @file 规则执行链路追踪中心（P1-12）
  @description 独立路由页面：按 traceId / 规则编码 / 时间 / 触发状态检索执行链路，
               支持回放、上下文快照查看、详细错误展示。
  @module views/execution/rule-engine/traces
  @author ydsz-pmis-team
  @since 1.5.0
-->
<template>
  <div class="trace-center">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">规则执行链路追踪中心</span>
          <div class="actions">
            <el-button :icon="Refresh" @click="fetchTraces" :loading="loading">刷新</el-button>
            <el-button @click="goBack">返回</el-button>
          </div>
        </div>
      </template>

      <!-- 筛选区 -->
      <el-form :inline="true" class="filter-form">
        <el-form-item label="traceId">
          <el-input v-model="filterTraceId" placeholder="按 traceId 精确查询" clearable style="width: 240px" />
        </el-form-item>
        <el-form-item label="规则编码">
          <el-input v-model="filterRuleCode" placeholder="按规则编码" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="触发状态">
          <el-select v-model="filterTriggered" placeholder="全部" clearable style="width: 120px">
            <el-option label="仅触发" :value="true" />
            <el-option label="仅未触发" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="fetchTraces">查询</el-button>
          <el-button text @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 链路列表 -->
      <!-- TODO P3: 待评估迁移 VirtualTable（listRecentTraces 上限 200 条，可能 >100；但含操作按钮/Tag 插槽，VirtualTable 仅支持 formatter 文本渲染，需先扩展组件支持插槽后再迁移） -->
      <el-table v-loading="loading" :data="filteredTraces" border stripe max-height="520">
        <el-table-column prop="traceId" label="Trace ID" width="220" show-overflow-tooltip />
        <el-table-column prop="ruleCode" label="规则编码" width="180" show-overflow-tooltip />
        <el-table-column prop="ruleName" label="规则名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="是否触发" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.triggered ? 'danger' : 'info'" size="small">
              {{ row.triggered ? '触发' : '未触发' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="严重度" width="100">
          <template #default="{ row }">
            <el-tag :type="severityType(row.severity)" size="small">
              {{ severityLabel(row.severity) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="elapsedMs" label="耗时(ms)" width="100" sortable />
        <el-table-column prop="scenario" label="场景" width="100" />
        <el-table-column prop="createdAt" label="执行时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button link type="warning" size="small" @click="replayTrace(row)">回放</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="执行链路详情" width="800px">
      <el-descriptions v-if="currentTrace" :column="2" border>
        <el-descriptions-item label="Trace ID">{{ currentTrace.traceId }}</el-descriptions-item>
        <el-descriptions-item label="规则编码">{{ currentTrace.ruleCode }}</el-descriptions-item>
        <el-descriptions-item label="规则名称">{{ currentTrace.ruleName }}</el-descriptions-item>
        <el-descriptions-item label="场景">{{ currentTrace.scenario }}</el-descriptions-item>
        <el-descriptions-item label="触发">
          <el-tag :type="currentTrace.triggered ? 'danger' : 'info'" size="small">
            {{ currentTrace.triggered ? '是' : '否' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="严重度">
          <el-tag :type="severityType(currentTrace.severity)" size="small">
            {{ severityLabel(currentTrace.severity) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="条件结果">{{ currentTrace.conditionResult }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ currentTrace.elapsedMs }}ms</el-descriptions-item>
        <el-descriptions-item label="错误" :span="2" v-if="currentTrace.errorMessage">
          <el-text type="danger">{{ currentTrace.errorMessage }}</el-text>
        </el-descriptions-item>
      </el-descriptions>
      <el-tabs style="margin-top: 16px">
        <el-tab-pane label="输入事实">
          <pre class="json-view">{{ formatJson(currentTrace?.factsSnapshot) }}</pre>
        </el-tab-pane>
        <el-tab-pane label="输出结果">
          <pre class="json-view">{{ formatJson(currentTrace?.resultSnapshot) }}</pre>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <!-- 回放结果对话框 -->
    <el-dialog v-model="replayVisible" title="回放结果" width="700px">
      <div v-if="replayResult">
        <el-alert
          :title="replayResult.diff?.summary || '回放中...'"
          :type="(replayResult.diff?.added?.length || 0) > 0 ? 'warning' : 'success'"
          :closable="false"
          show-icon
        />
        <el-descriptions :column="2" border style="margin-top: 12px">
          <el-descriptions-item label="traceId">{{ replayResult.traceId }}</el-descriptions-item>
          <el-descriptions-item label="历史触发">{{ replayResult.historicalTraces?.length || 0 }} 条</el-descriptions-item>
          <el-descriptions-item label="当前触发" :span="2">
            <el-tag v-for="r in replayResult.currentResults" :key="r.ruleCode" size="small" style="margin: 2px">
              {{ r.ruleCode }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-divider>差异分析</el-divider>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-card shadow="never" class="diff-card diff-added">
              <div class="diff-card-title">新增触发</div>
              <div class="diff-card-count">{{ replayResult.diff?.added?.length || 0 }}</div>
              <div class="diff-card-list">
                <el-tag v-for="c in replayResult.diff?.added" :key="c" type="success" size="small">{{ c }}</el-tag>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never" class="diff-card diff-removed">
              <div class="diff-card-title">移除触发</div>
              <div class="diff-card-count">{{ replayResult.diff?.removed?.length || 0 }}</div>
              <div class="diff-card-list">
                <el-tag v-for="c in replayResult.diff?.removed" :key="c" type="warning" size="small">{{ c }}</el-tag>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never" class="diff-card diff-unchanged">
              <div class="diff-card-title">保持不变</div>
              <div class="diff-card-count">{{ replayResult.diff?.unchanged?.length || 0 }}</div>
              <div class="diff-card-list">
                <el-tag v-for="c in replayResult.diff?.unchanged" :key="c" type="info" size="small">{{ c }}</el-tag>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  getTrace, getTracesByRule, listRecentTraces, replayTrace,
  type ExecutionTrace, type ReplayResult,
} from '@/api/execution/rule-engine'

defineOptions({ name: 'RuleEngineTraceCenter' })

const router = useRouter()
const loading = ref(false)
const traces = ref<ExecutionTrace[]>([])

const filterTraceId = ref('')
const filterRuleCode = ref('')
const filterTriggered = ref<boolean | null>(null)

const detailVisible = ref(false)
const currentTrace = ref<ExecutionTrace | null>(null)

const replayVisible = ref(false)
const replayResult = ref<ReplayResult | null>(null)

const filteredTraces = computed(() => {
  let list = traces.value
  if (filterTraceId.value) {
    list = list.filter(t => t.traceId?.includes(filterTraceId.value))
  }
  if (filterRuleCode.value) {
    list = list.filter(t => t.ruleCode?.includes(filterRuleCode.value))
  }
  if (filterTriggered.value !== null) {
    list = list.filter(t => t.triggered === filterTriggered.value)
  }
  return list
})

function severityType(s: string): 'danger' | 'warning' | 'info' | 'success' | 'primary' {
  if (s === 'RED') return 'danger'
  if (s === 'YELLOW') return 'warning'
  return 'info'
}
function severityLabel(s: string): string {
  if (s === 'RED') return '红色'
  if (s === 'YELLOW') return '黄色'
  if (s === 'NORMAL') return '通知'
  return s || '-'
}
function formatJson(obj: any): string {
  if (!obj) return '（空）'
  try {
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(obj)
  }
}

async function fetchTraces() {
  loading.value = true
  try {
    let res
    if (filterTraceId.value) {
      res = await getTrace(filterTraceId.value)
      traces.value = res.data || []
    } else if (filterRuleCode.value) {
      res = await getTracesByRule(filterRuleCode.value, 100)
      traces.value = res.data || []
    } else {
      res = await listRecentTraces(200)
      traces.value = res.data || []
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '查询失败')
  } finally {
    loading.value = false
  }
}

function resetFilter() {
  filterTraceId.value = ''
  filterRuleCode.value = ''
  filterTriggered.value = null
  fetchTraces()
}

function openDetail(row: ExecutionTrace) {
  currentTrace.value = row
  detailVisible.value = true
}

async function replayTraceRow(row: ExecutionTrace) {
  try {
    const res = await replayTrace(row.traceId)
    if (res.code === 0) {
      replayResult.value = res.data
      replayVisible.value = true
    } else {
      ElMessage.error(res.message || '回放失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '回放异常')
  }
}

function goBack() {
  router.push('/rule-engine')
}

onMounted(() => {
  fetchTraces()
})
</script>

<style scoped lang="scss">
.trace-center {
  padding: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  .title { font-weight: 600; font-size: 16px; }
  .actions { display: flex; gap: 8px; }
}
.filter-form { margin-bottom: 12px; }

.json-view {
  background: #1e293b;
  color: #e2e8f0;
  padding: 12px;
  border-radius: 4px;
  max-height: 360px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.5;
  font-family: 'JetBrains Mono', 'Courier New', monospace;
}

.diff-card {
  text-align: center;
  .diff-card-title { font-size: 12px; color: #64748b; margin-bottom: 8px; }
  .diff-card-count { font-size: 24px; font-weight: 600; margin-bottom: 8px; }
  .diff-card-list { display: flex; flex-wrap: wrap; gap: 4px; justify-content: center; }
}
.diff-added .diff-card-count { color: #16a34a; }
.diff-removed .diff-card-count { color: #f59e0b; }
.diff-unchanged .diff-card-count { color: #3b82f6; }
</style>
