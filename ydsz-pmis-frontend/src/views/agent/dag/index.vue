<!--
  @fileoverview DAG 工作流编排引擎管理页面

  - 业务模块归属: AI Agent DAG 工作流编排
  - 关键能力: DAG 定义列表 + 执行监控 + 实例详情 + 节点状态可视化
  - 关联的后端接口: @/api/agent/dag

  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  createDefinition,
  executeDag,
  getInstance,
  listNodeInstances,
  pageDefinitions,
  pageInstances,
} from '@/api/agent/dag'
import type {
  DagDefinitionDO,
  DagExecutionResult,
  DagInstanceDO,
  DagNodeInstanceDO,
  DagNodeStatus,
} from '@/api/agent/dag/types'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

// ============= 定义列表 =============
const loading = ref(false)
const list = ref<DagDefinitionDO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

async function loadList() {
  loading.value = true
  try {
    const { data } = await pageDefinitions(pageNo.value, pageSize.value)
    const result = data as any
    list.value = result?.list ?? result?.records ?? []
    total.value = result?.total ?? 0
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.dag.messages.loadFailed'))
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) { pageNo.value = p; loadList() }
function onSizeChange(s: number) { pageSize.value = s; pageNo.value = 1; loadList() }

// ============= 创建 DAG =============
const createDialogVisible = ref(false)
const createForm = reactive({
  name: '',
  description: '',
  dagJson: '',
})
const creating = ref(false)

async function handleCreate() {
  if (!createForm.name) {
    ElMessage.warning(t('agent.dag.messages.nameRequired'))
    return
  }
  let dagDef
  try {
    dagDef = JSON.parse(createForm.dagJson)
  } catch {
    ElMessage.error(t('agent.dag.messages.invalidJson'))
    return
  }
  dagDef.name = createForm.name
  dagDef.description = createForm.description
  creating.value = true
  try {
    await createDefinition(dagDef)
    ElMessage.success(t('agent.dag.messages.createSuccess'))
    createDialogVisible.value = false
    createForm.name = ''
    createForm.description = ''
    createForm.dagJson = ''
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.dag.messages.createFailed'))
  } finally {
    creating.value = false
  }
}

function openCreateDialog() {
  createForm.dagJson = JSON.stringify({
    name: '',
    nodes: [
      {
        name: 'node1',
        agentType: 'RISK_WARNING',
        dependencies: [],
        inputs: {},
      },
    ],
    failureStrategy: 'ABORT',
    defaultTimeoutMs: 30000,
    maxRetries: 3,
  }, null, 2)
  createDialogVisible.value = true
}

// ============= 执行 DAG =============
const executing = ref(false)
const executionResult = ref<DagExecutionResult | null>(null)
const executeDialogVisible = ref(false)
const executeInputs = ref('')

async function handleExecute(row: DagDefinitionDO) {
  executing.value = true
  executionResult.value = null
  executeDialogVisible.value = true
  try {
    let inputs: Record<string, unknown> | undefined
    if (executeInputs.value) {
      try {
        inputs = JSON.parse(executeInputs.value)
      } catch {
        ElMessage.warning(t('agent.dag.messages.invalidInputs'))
        executing.value = false
        return
      }
    }
    const { data } = await executeDag(row.id!, inputs)
    executionResult.value = data as DagExecutionResult
    ElMessage.success(t('agent.dag.messages.executeSuccess'))
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.dag.messages.executeFailed'))
  } finally {
    executing.value = false
  }
}

// ============= 实例历史 =============
const instanceDialogVisible = ref(false)
const instanceList = ref<DagInstanceDO[]>([])
const instanceTotal = ref(0)
const instancePageNo = ref(1)
const currentDefinitionId = ref('')
const instanceLoading = ref(false)

async function openInstanceHistory(row: DagDefinitionDO) {
  currentDefinitionId.value = row.id!
  instanceDialogVisible.value = true
  instancePageNo.value = 1
  await loadInstances()
}

async function loadInstances() {
  instanceLoading.value = true
  try {
    const { data } = await pageInstances(currentDefinitionId.value, instancePageNo.value, 20)
    const result = data as any
    instanceList.value = result?.list ?? result?.records ?? []
    instanceTotal.value = result?.total ?? 0
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.dag.messages.loadFailed'))
  } finally {
    instanceLoading.value = false
  }
}

// ============= 节点详情 =============
const nodeDialogVisible = ref(false)
const nodeList = ref<DagNodeInstanceDO[]>([])
const nodeLoading = ref(false)

async function openNodeDetail(row: DagInstanceDO) {
  nodeDialogVisible.value = true
  nodeLoading.value = true
  try {
    const { data } = await listNodeInstances(row.id)
    nodeList.value = (data as DagNodeInstanceDO[]) || []
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.dag.messages.loadFailed'))
  } finally {
    nodeLoading.value = false
  }
}

// ============= 工具方法 =============
function statusTagType(status: string): 'success' | 'info' | 'warning' | 'danger' {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'RUNNING': return 'warning'
    case 'FAILED': return 'danger'
    case 'PARTIAL': return 'warning'
    case 'PENDING': return 'info'
    case 'SKIPPED': return 'info'
    default: return 'info'
  }
}

function nodeStatusColor(status: DagNodeStatus): string {
  switch (status) {
    case 'SUCCESS': return '#67C23A'
    case 'RUNNING': return '#E6A23C'
    case 'FAILED': return '#F56C6C'
    case 'SKIPPED': return '#909399'
    default: return '#C0C4CC'
  }
}

const SAMPLE_DAG_TEMPLATE = computed(() => t('agent.dag.create.template'))

onMounted(() => {
  loadList()
})
</script>

<template>
  <div class="dag-page">
    <!-- 工具栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-button v-permission="[PC.AGENT_DAG_CREATE]" type="primary" :icon="'Plus'" @click="openCreateDialog">
            {{ t('agent.dag.buttons.create') }}
          </el-button>
          <el-button :icon="'Refresh'" @click="loadList">{{ t('agent.dag.buttons.refresh') }}</el-button>
        </div>
      </div>
    </el-card>

    <!-- DAG 定义列表 -->
    <el-card shadow="never" style="margin-top: 16px">
      <vxe-table :data="list" :loading="loading" stripe height="auto">
        <vxe-column type="seq" width="56" title="#" />
        <vxe-column field="name" :title="t('agent.dag.columns.name')" min-width="180" show-overflow />
        <vxe-column field="description" :title="t('agent.dag.columns.description')" min-width="200" show-overflow />
        <vxe-column field="failureStrategy" :title="t('agent.dag.columns.strategy')" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ row.failureStrategy || 'ABORT' }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="defaultTimeoutMs" :title="t('agent.dag.columns.timeout')" width="100">
          <template #default="{ row }">{{ row.defaultTimeoutMs || 30000 }}ms</template>
        </vxe-column>
        <vxe-column field="maxRetries" :title="t('agent.dag.columns.retries')" width="80" />
        <vxe-column field="createdAt" :title="t('agent.dag.columns.createdAt')" width="170" />
        <vxe-column :title="t('agent.dag.columns.action')" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="[PC.AGENT_DAG_RUN]" link type="primary" size="small"
              :icon="'VideoPlay'" @click="handleExecute(row)">
              {{ t('agent.dag.buttons.execute') }}
            </el-button>
            <el-button link type="info" size="small" :icon="'Files'" @click="openInstanceHistory(row)">
              {{ t('agent.dag.buttons.history') }}
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
      <el-pagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </el-card>

    <!-- 创建 DAG 对话框 -->
    <el-dialog v-model="createDialogVisible" :title="t('agent.dag.create.title')" width="700px">
      <el-form label-width="100px">
        <el-form-item :label="t('agent.dag.create.name')">
          <el-input v-model="createForm.name" :placeholder="t('agent.dag.create.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('agent.dag.create.description')">
          <el-input v-model="createForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('agent.dag.create.json')">
          <el-input v-model="createForm.dagJson" type="textarea" :rows="15"
            :placeholder="SAMPLE_DAG_TEMPLATE" style="font-family: monospace; font-size: 12px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 执行结果对话框 -->
    <el-dialog v-model="executeDialogVisible" :title="t('agent.dag.execute.title')" width="900px" top="5vh">
      <el-form v-if="!executionResult" label-width="80px">
        <el-form-item :label="t('agent.dag.execute.inputs')">
          <el-input v-model="executeInputs" type="textarea" :rows="5"
            placeholder='{"key":"value"}' style="font-family: monospace" />
        </el-form-item>
      </el-form>

      <div v-if="executionResult" class="exec-result">
        <!-- KPI -->
        <el-row :gutter="12" class="kpi-row">
          <el-col :span="6">
            <el-card shadow="hover" class="kpi-card">
              <div class="kpi-title">{{ t('agent.dag.execute.status') }}</div>
              <el-tag :type="statusTagType(executionResult.status)" effect="dark" size="large">
                {{ executionResult.status }}
              </el-tag>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover" class="kpi-card">
              <div class="kpi-title">{{ t('agent.dag.execute.totalCost') }}</div>
              <div class="kpi-value">{{ executionResult.totalCostMs }}ms</div>
            </el-card>
          </el-col>
          <el-col :span="4">
            <el-card shadow="hover" class="kpi-card success">
              <div class="kpi-title">{{ t('agent.dag.execute.success') }}</div>
              <div class="kpi-value">{{ executionResult.successCount }}</div>
            </el-card>
          </el-col>
          <el-col :span="4">
            <el-card shadow="hover" class="kpi-card danger">
              <div class="kpi-title">{{ t('agent.dag.execute.failed') }}</div>
              <div class="kpi-value">{{ executionResult.failedCount }}</div>
            </el-card>
          </el-col>
          <el-col :span="4">
            <el-card shadow="hover" class="kpi-card">
              <div class="kpi-title">{{ t('agent.dag.execute.skipped') }}</div>
              <div class="kpi-value">{{ executionResult.skippedCount }}</div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 节点状态 -->
        <el-card shadow="never" :header="t('agent.dag.execute.nodeStatus')" style="margin-top: 12px">
          <div class="node-status-grid">
            <div v-for="(status, name) in executionResult.nodeStatuses" :key="name" class="node-status-item">
              <span class="node-dot" :style="{ background: nodeStatusColor(status) }" />
              <span class="node-name">{{ name }}</span>
              <el-tag :type="statusTagType(status)" size="small">{{ status }}</el-tag>
              <span v-if="executionResult.nodeRetryCounts[name] > 0" class="retry-badge">
                ↻ {{ executionResult.nodeRetryCounts[name] }}
              </span>
            </div>
          </div>
        </el-card>

        <!-- Trace 日志 -->
        <el-card shadow="never" :header="t('agent.dag.execute.trace')" style="margin-top: 12px">
          <el-timeline>
            <el-timeline-item
              v-for="(trace, idx) in executionResult.traces"
              :key="idx"
              :type="trace.event === 'FAILED' ? 'danger' : trace.event === 'SUCCESS' ? 'success' : 'primary'"
              :timestamp="trace.timestamp || ''"
              size="large"
            >
              <div class="trace-item">
                <el-tag size="small" :type="trace.event === 'FAILED' ? 'danger' : 'info'">{{ trace.event }}</el-tag>
                <span v-if="trace.nodeName" class="trace-node">{{ trace.nodeName }}</span>
                <span class="trace-msg">{{ trace.message }}</span>
              </div>
            </el-timeline-item>
          </el-timeline>
        </el-card>

        <!-- 错误信息 -->
        <el-card v-if="Object.keys(executionResult.nodeErrors).length > 0" shadow="never"
          :header="t('agent.dag.execute.errors')" style="margin-top: 12px">
          <el-alert v-for="(err, node) in executionResult.nodeErrors" :key="node"
            :title="`${node}: ${err}`" type="error" :closable="false" style="margin-bottom: 8px" />
        </el-card>
      </div>

      <template #footer>
        <el-button @click="executeDialogVisible = false">{{ t('common.close') }}</el-button>
        <el-button v-if="!executionResult" type="primary" :loading="executing"
          @click="handleExecute({ id: currentDefinitionId } as DagDefinitionDO)">
          {{ t('agent.dag.buttons.execute') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 执行历史对话框 -->
    <el-dialog v-model="instanceDialogVisible" :title="t('agent.dag.history.title')" width="900px">
      <vxe-table :data="instanceList" :loading="instanceLoading" stripe max-height="400">
        <vxe-column type="seq" width="56" title="#" />
        <vxe-column field="id" :title="t('agent.dag.history.instanceId')" width="200" show-overflow />
        <vxe-column field="status" :title="t('agent.dag.history.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="totalCostMs" :title="t('agent.dag.history.cost')" width="100">
          <template #default="{ row }">{{ row.totalCostMs ?? '-' }}ms</template>
        </vxe-column>
        <vxe-column field="successCount" :title="t('agent.dag.history.success')" width="80" />
        <vxe-column field="failedCount" :title="t('agent.dag.history.failed')" width="80" />
        <vxe-column field="createdAt" :title="t('agent.dag.history.createdAt')" width="170" />
        <vxe-column :title="t('agent.dag.columns.action')" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openNodeDetail(row)">
              {{ t('agent.dag.buttons.nodeDetail') }}
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
      <el-pagination
        v-model:current-page="instancePageNo"
        :total="instanceTotal"
        :page-size="20"
        layout="total, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="loadInstances"
      />
    </el-dialog>

    <!-- 节点明细对话框 -->
    <el-dialog v-model="nodeDialogVisible" :title="t('agent.dag.nodeDetail.title')" width="900px">
      <vxe-table :data="nodeList" :loading="nodeLoading" stripe max-height="500">
        <vxe-column type="seq" width="56" title="#" />
        <vxe-column field="nodeName" :title="t('agent.dag.nodeDetail.name')" min-width="150" />
        <vxe-column field="agentType" :title="t('agent.dag.nodeDetail.agentType')" width="160">
          <template #default="{ row }">
            <el-tag v-if="row.agentType" size="small">{{ row.agentType }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="status" :title="t('agent.dag.nodeDetail.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="retryCount" :title="t('agent.dag.nodeDetail.retries')" width="80" />
        <vxe-column field="costMs" :title="t('agent.dag.nodeDetail.cost')" width="100">
          <template #default="{ row }">{{ row.costMs ?? '-' }}ms</template>
        </vxe-column>
        <vxe-column field="startedAt" :title="t('agent.dag.nodeDetail.startedAt')" width="170" />
        <vxe-column field="errorMsg" :title="t('agent.dag.nodeDetail.error')" min-width="200" show-overflow>
          <template #default="{ row }">
            <el-text v-if="row.errorMsg" type="danger" size="small">{{ row.errorMsg }}</el-text>
            <span v-else>-</span>
          </template>
        </vxe-column>
      </vxe-table>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.dag-page {
  .toolbar-card { margin-bottom: 0; }
  .toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .kpi-row { margin-bottom: 8px; }
  .kpi-card {
    text-align: center;
    .kpi-title { font-size: 12px; color: var(--el-text-color-secondary); }
    .kpi-value { font-size: 22px; font-weight: 600; margin-top: 8px; }
    &.success .kpi-value { color: var(--el-color-success); }
    &.danger .kpi-value { color: var(--el-color-danger); }
  }
  .node-status-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 8px;
  }
  .node-status-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 12px;
    background: var(--el-fill-color-light);
    border-radius: 6px;
    .node-dot {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      flex-shrink: 0;
    }
    .node-name { flex: 1; font-size: 13px; }
    .retry-badge { font-size: 12px; color: var(--el-color-warning); }
  }
  .trace-item {
    display: flex;
    align-items: center;
    gap: 8px;
    .trace-node { font-weight: 600; font-size: 13px; }
    .trace-msg { font-size: 13px; color: var(--el-text-color-secondary); }
  }
}
</style>
